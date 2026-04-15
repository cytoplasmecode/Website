---
title: "The Cost of Indirection"
date: 2026-04-10
tags: ["systems", "performance", "design"]
summary: "Abstraction is one of the most powerful tools in software. It's also one of the most expensive. The cost is often hidden — and that's the problem."
---

Abstraction is one of the most powerful tools in software. It's also one of the most expensive. The cost is often hidden — and that's the problem.

## What indirection buys you

When you call `read(fd, buf, count)`, you're not thinking about interrupt handlers, DMA transfers, or page cache management. The abstraction does what abstractions are supposed to do: it lets you think at the right level for the problem at hand.

This is genuinely powerful. Well-designed abstractions let you build complex systems from simple, composable parts. They hide irrelevant detail. They create seams for testing, for swapping implementations, for reasoning locally without holding the whole system in your head.

## What indirection costs you

The price is indirection — both literal and figurative.

Literally: a virtual function call, a pointer dereference, a cache miss. Modern CPUs are extraordinary at executing sequential code with predictable memory access patterns. They are much worse at chasing pointers through memory. An indirect function call can cost 10–20 cycles when the branch predictor misses. Multiplied across a hot loop, this adds up.

Figuratively: cognitive overhead. Every layer of abstraction is a translation you need to perform when debugging. "Why is this slow?" becomes "what does *this* actually do?" — and the answer is buried three layers down, in code you didn't write.

## The abstraction is always leaky

Every abstraction leaks eventually. The file system abstraction doesn't tell you that sequential reads are faster than random reads — until you're profiling a database and suddenly the file system is very much your problem. The network abstraction doesn't tell you about head-of-line blocking — until you're debugging latency spikes in production.

The leaks are fine. The problem is forgetting that the leaks exist.

## Practical heuristics

A few things I've found useful:

**Know one level down.** You don't need to understand the entire stack, but you should understand what the abstractions you use are doing, roughly. Know that `malloc` isn't free. Know that a HashMap does more work than a slice lookup for small collections. Know that spawning a goroutine has a cost.

**Profile before optimizing.** The cost of indirection is real but often not the bottleneck. Measure before you decide which abstractions to keep and which to collapse.

**Name the abstraction's contract clearly.** A lot of abstraction cost comes from misuse — using something for a purpose it wasn't designed for. Clear naming and documented contracts reduce this.

**Trust the compiler, but verify.** Compilers are very good at inlining and devirtualizing. Look at what gets generated before assuming the abstraction is expensive.

---

Abstraction isn't bad. It's the foundation of everything that works at scale. But it isn't free, and treating it as free is how you end up with systems that are elegant on the whiteboard and inexplicable in production.

Know the cost. Decide deliberately.
