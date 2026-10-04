# Network tuning guide

What Krypton does for latency and bandwidth, what it cannot do, and which server, JVM and operating
system settings are worth changing. Numbers marked **measured** come from a loopback experiment run
while preparing this guide (method at the end); everything else is a recommendation with its source
named. Always test changes on a staging server before production.

## 1. What a mod can and cannot change

| Problem | Can this mod fix it? | What to do instead |
|---|---|---|
| Nagle's algorithm stalls (about 40 ms) | Yes, as a safety net (`TCP_NODELAY` is enforced) | Nothing further needed |
| Too many tiny TCP segments | Already handled: the game flushes once per tick | Do not flush per packet in other mods |
| Packet size / bandwidth | Partly: compression level is tunable | See section 3 |
| IPv6 vs IPv4 selection on the **client** | No (the client resolves and connects) | Players install a Happy Eyeballs client mod (section 4) |
| IPv6 on the **server** | No (it is bind address and OS config) | Section 4 |
| Routing between player and server | No (ISPs / BGP / hosting provider) | Choose a host with good peering near your players |
| Making chunk packets "smaller" by changing their format | No: vanilla clients would disconnect | Reduce how much is sent (view distance) and compress better |

## 2. Latency

### Nagle's algorithm and delayed ACK (measured)
A request made of two small writes followed by a wait for a reply took **43.8 ms per round trip with
Nagle's algorithm on, and 0.025 ms with `TCP_NODELAY` on** (about 1,700 times faster, loopback, 200
round trips, two runs). On a real network the stall is the same size; it comes from Nagle's algorithm
waiting for an ACK that the receiver delays.

Krypton now enables `TCP_NODELAY` on every TCP connection when compression is set up (it is a no-op
if the game already did it). Velocity, the most widely used Minecraft proxy, also sets it explicitly,
together with other low-latency socket options.

### One flush per tick, not per packet (measured)
Sending 20,000 small (60-byte) packets in 200 ticks:

| Strategy | Time | TCP segments |
|---|---|---|
| Flush after every packet | 548-784 ms | 27,183-28,884 |
| One flush per tick | 72-100 ms | 224-235 |

That is about **7-8 times faster with about 120 times fewer segments**. Vanilla already buffers during the
tick and flushes once at the end (the Canvas documentation describes this, and notes that Folia
disables it, which defeats the batching). If you use a server fork or mods that flush every packet,
that is a real latency and CPU cost.

### Linux kernel settings (server)
Widely used for latency-sensitive TCP servers (Arch Wiki, LiveConfig, Linux kernel documentation):

```
# /etc/sysctl.d/99-minecraft-network.conf
net.core.default_qdisc = fq
net.ipv4.tcp_congestion_control = bbr      # needs: modprobe tcp_bbr
net.ipv4.tcp_slow_start_after_idle = 0     # do not shrink the window after a quiet moment
net.ipv4.tcp_fastopen = 3                  # lets repeat connections send data in the first packet
```

Then run `sysctl --system`. Caveats: BBR behaves differently from CUBIC under packet loss and with
other flows, so compare on your own traffic. `tcp_notsent_lowat` (for example 16384) can reduce
queuing delay on busy servers but is mainly proven for web workloads, so treat it as optional.

### Server and JVM
- `use-native-transport=true` in `server.properties` (the default on Linux) uses epoll, which has lower
  per-packet overhead than the Java NIO fallback.
- Keep `network-compression-threshold=256` unless you have measured a reason to change it. A higher
  value saves CPU and costs bandwidth; `-1` disables compression and is only sensible on a fast
  private network.
- Latency is also tick latency: a server that cannot keep 20 TPS delays every packet. Use a current
  JDK 25 and an up-to-date server software.

## 3. Packet size and bandwidth

- **Compression level** (new): `-Dkrypton.compression-level=<1-9>`, default `4`. Lower is faster and
  sends more bytes; higher sends fewer bytes and uses more CPU. Only affects what this side sends.
  Measure on your own traffic before changing it; this guide does not claim a best value because
  chunk data varies a lot between worlds.
- **Threshold**: see above. Packets under the threshold are not compressed at all.
- **Chunk traffic**: the amount of chunk data sent grows with the square of the view distance, so
  lowering `view-distance` (and `simulation-distance`) by one or two is the most effective way to
  cut bandwidth without touching the protocol.
- **Frame size check** (new): the length prefix of a packet is limited to 3 bytes (2,097,151 bytes). Krypton
  now fails on the sending side with a clear error for anything larger, the same rule the receiving
  side already enforces.

## 4. IPv6

- **Client**: Minecraft connects using the first address it resolves, which is usually IPv4, so a
  server that supports both is still reached over IPv4 and a broken IPv6 path cannot be skipped
  quickly. The "Happy Eyeballs" algorithm (RFC 6555, updated by RFC 8305) fixes this by racing both.
  The client mod **CraftyEyeballs** implements it; it is client-only and does nothing on a server.
- The launcher has historically set `java.net.preferIPv4Stack=true` (Mojang bug MC-3776). Players who
  want IPv6 may have to remove that flag.
- **Server**: leave `server-ip` empty so it binds to all addresses (dual stack), publish both `A` and
  `AAAA` DNS records, and make sure the firewall allows the port over IPv6 as well. Do not publish an
  `AAAA` record unless IPv6 really works end to end, or players with a broken IPv6 path may see delays.

## 5. How the experiments were run
Java 21, Netty 4.1.48 (NIO), loopback interface, one machine. Test A: client writes 4 bytes then 100
bytes (separately flushed), server replies with 1 byte; averaged over 200 round trips, with
`TCP_NODELAY` off and on. Test B: 200 ticks of 100 packets of 60 bytes, `TCP_NODELAY` on, timed until
the receiver had every byte; segments counted from the kernel's `OutSegs` counter before and after.
Loopback has no real network delay, so absolute numbers on the internet will differ, but the
direction and the reason (Nagle plus delayed ACK, and one syscall per flush) are the same.
