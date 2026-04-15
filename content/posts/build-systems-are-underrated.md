---
title: "Build Systems Are Underrated"
date: 2026-03-28
tags: ["tooling", "devex", "builds"]
summary: "Developers spend a surprising fraction of their working lives waiting for builds, running tests, and untangling dependency graphs. The build system is the silent tax on everything you do."
---

Developers spend a surprising fraction of their working lives waiting for builds, running tests, and untangling dependency graphs. The build system is the silent tax on everything you do — and most teams treat it as an afterthought.

## The invisible cost

A build that takes 8 minutes instead of 2 minutes isn't a 6 minute annoyance. It's a context switch. You open Twitter. You check Slack. You make coffee. When you come back, the thread of thought you were holding has frayed.

Multiply this by a team of 20, compounded over months, and slow builds are one of the most expensive things you can have. They're just slow enough that nobody writes a P0 ticket for them.

## What makes a build system good

**Correctness.** A build is correct if its outputs are a deterministic function of its inputs. This sounds obvious but most build systems fail at it intermittently — `make` with handwritten rules is especially notorious. Incorrect builds produce "works on my machine" bugs that are extremely expensive to debug.

**Incrementality.** Only rebuild what changed. This requires understanding the dependency graph precisely. Tools that get this wrong (by being too conservative) are slow; tools that get it wrong (by being too aggressive) produce broken artifacts. Getting it right is hard, which is why projects like Bazel, Buck, and Pants invest so heavily in it.

**Parallelism.** A correct dependency graph tells you what can run in parallel. Most modern build systems exploit this. The ones that don't are leaving significant performance on the table.

**Reproducibility.** The same inputs should produce the same outputs, on any machine, at any time. This enables caching — both local and remote — and eliminates an entire class of environment-related bugs.

## Remote caching is a superpower

Once you have reproducible builds, remote caching becomes available. The idea is simple: if a build action has been run before with the same inputs, use the cached output instead of rerunning it.

In practice, this means:
- CI doesn't rebuild code that hasn't changed
- New developers don't spend their first day waiting for a full build
- A clean checkout can be nearly instant if the cache is warm

The first time a team turns on remote caching they typically see CI times drop by 60–80%. It's one of the highest-leverage infrastructure investments you can make.

## The cultural problem

Build systems get neglected because the improvements they offer are diffuse. Saving 5 minutes per developer per day is enormous over a year, but it doesn't show up on a roadmap. It doesn't close a ticket. The person who spends a week making builds faster rarely gets credit proportional to the value they created.

The best engineering teams I've seen treat build infrastructure as a first-class concern. They assign ownership. They track build times as a metric. They treat a build regression the way they'd treat a performance regression in production.

---

Your build system is the thing everything runs through. Investing in it pays dividends on every other engineering activity. It just requires treating it like the infrastructure it is.
