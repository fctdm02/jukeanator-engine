# Nayax VPOS Touch: Pulse Integration

## Overview

The Nayax VPOS Touch is a payment terminal that can communicate with a vending machine, kiosk, or other controller using several protocols, including **MDB** and **Pulse**.

Unlike a traditional bill acceptor, which commonly emits a pulse for each accepted bill, a card reader first processes and authorizes the payment. If configured for Pulse mode, it then converts the authorized credit into electrical pulses for the connected controller.

## Does the VPOS Touch support pulse integration?

Yes. Nayax documentation identifies **Pulse Standard** variants of the VPOS Touch and provides a dedicated Pulse Manual.

The relevant U.S. catalog entries include:

| Catalog number   | Description                                         |
| ---------------- | --------------------------------------------------- |
| `ST4GVZ003B01S2` | VPOS TOUCH, 4G US (Verizon), Pulse Standard, black  |
| `ST4GVZ003Y01S2` | VPOS TOUCH, 4G US (Verizon), Pulse Standard, yellow |
| `ST4GUS003B01S2` | VPOS TOUCH, 4G US, Pulse Standard, black, Tele2     |
| `ST4GUS003Y01S2` | VPOS TOUCH, 4G US, Pulse Standard, yellow, Tele2    |

> **Note:** These are catalog entries, not a guarantee that every VPOS Touch unit supports every pulse configuration. Confirm the exact part number and cellular carrier with Nayax or the supplier before purchasing.

## How Pulse mode works

A typical transaction flow is:

```text
Customer taps card
        ↓
Nayax authorizes payment
        ↓
VPOS Touch generates configured credit pulses
        ↓
Controller counts the pulses
        ↓
Controller grants the corresponding credit
```

The pulse interface is conceptually similar to the credit output of a bill acceptor. However, the reader must be configured for Pulse mode, and the electrical interface must match the connected controller.

## Pulse mode versus MDB

| Pulse                                                                     | MDB                                                   |
| ------------------------------------------------------------------------- | ----------------------------------------------------- |
| Simple electrical credit signaling                                        | Structured digital vending protocol                   |
| Controller counts configured pulses                                       | Controller exchanges messages with the reader         |
| Suitable for a controller that already accepts bill-acceptor-style pulses | Suitable for a controller with MDB support            |
| Requires correct pulse value, wiring, polarity, and timing                | Requires compatible MDB hardware and protocol support |

The VPOS Touch should not be assumed to emit pulses in every installation. The actual behavior depends on the selected machine protocol and configuration.

## How many pulses does a $10 charge produce?

There is **no universal pulse count** for a $10.00 card transaction. The number depends on the configured **credit per pulse**.

The basic calculation is:

```text
Number of pulses = Transaction amount ÷ Credit per pulse
```

### Examples

| Credit per pulse | Pulses for a $10.00 charge |
| ---------------: | -------------------------: |
|            $0.10 |                        100 |
|            $0.25 |                         40 |
|            $0.50 |                         20 |
|        **$1.00** |                     **10** |
|            $2.00 |                          5 |

Therefore:

> If the VPOS Touch is configured for **$1.00 per pulse**, a successful **$10.00** transaction produces **10 pulses**.

If it is configured for **$0.50 per pulse**, the same transaction produces **20 pulses**.

## Single-price and multiple-price configurations

The Pulse Manual describes configurations in which the pulse count corresponds to a configured price.

For example, with a credit value of `$0.50` per pulse:

| Pulses |  Price |
| -----: | -----: |
|      3 |  $1.50 |
|      5 |  $2.50 |
|      6 |  $3.00 |
|     10 |  $5.00 |
|     18 |  $9.00 |
|     22 | $11.00 |

The exact behavior depends on whether the machine is configured for a single-price or multiple-price setup.

For a jukebox that uses one pulse per dollar, the intended configuration would be:

```text
Credit per pulse: $1.00
$10.00 transaction: 10 pulses
```

The actual settings must be verified in the device's Pulse configuration.

## Important integration details

Before connecting a VPOS Touch to a jukebox or custom controller, verify:

* Exact VPOS Touch hardware revision and catalog number
* Pulse mode is enabled
* Credit-per-pulse configuration
* Pulse output wiring and signal polarity
* Required voltage levels
* Pulse width and timing
* Whether a relay is required
* Inhibit input behavior
* Handling of authorization failures, cancellations, timeouts, and refunds

The physical wiring should be taken from the VPOS Touch Pulse Hardware Installation guide rather than inferred from the general product description.

## Official documentation

### Nayax Pulse Manual

Official Nayax Help Center:

https://nayax-u.nayax.com/article/nayax-pulse-manual-11705

### Pulse Hardware Installation Guide

Official Nayax documentation:

https://nayax-u.nayax.com/article/pulse-hardware-installation-11793

### VPOS Touch Product Catalog

The catalog contains the VPOS Touch **Pulse Standard** model entries.

## Summary

The Nayax VPOS Touch supports Pulse integration when the appropriate Pulse variant and configuration are used.

For a **$10.00** card transaction:

* At **$1.00 per pulse**: **10 pulses**
* At **$0.50 per pulse**: **20 pulses**
* At **$0.25 per pulse**: **40 pulses**

The pulse count is determined by the configured credit value, not by the card reader alone.
