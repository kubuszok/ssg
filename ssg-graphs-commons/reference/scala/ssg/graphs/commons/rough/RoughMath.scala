/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs RNG foundation (randomSeed + Lehmer/MINSTD `Random`) — Scala 3 port
 *
 * Original source: roughjs (src/math.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: TS module functions/`class Random` -> `object RoughMath` (`randomSeed`)
 *     + a sibling `final class Random` in the same `rough` package. roughjs's `Random`
 *     name is kept verbatim (this is the roughjs foundation; no other `Random` lives in
 *     `ssg/graphs/commons/rough`).
 *   Convention (PRNG byte-exactness, critical): `Random.next` is the Lehmer/MINSTD
 *     generator `((2**31 - 1) & (this.seed = Math.imul(48271, this.seed))) / 2**31`.
 *     `Math.imul(48271, seed)` is a 32-bit SIGNED integer multiply; the seed is stored
 *     as a Scala `Int`, and `48271 * seed` (Int * Int) already wraps mod 2^32 to a
 *     signed 32-bit result on JVM, Scala.js AND Scala Native — so `48271 * seed` ==
 *     `Math.imul(48271, seed)` for every input, including overflow/negative cases
 *     (verified vs the Node oracle: imul(48271, 123456789) == -2031945029 == the Int
 *     product). Order of operations is preserved exactly: FIRST the seed is reassigned
 *     to the full signed 32-bit product (sign bit RETAINED — the stored seed is NOT
 *     masked), THEN the RETURNED value masks it with `0x7FFFFFFF` (= 2**31 - 1) and
 *     divides by `2147483648.0` (= 2**31). Masking only the return (not the stored
 *     seed) is REQUIRED — it is NOT merely a structural nicety.
 *     (Mask-stored-seed note: for every seed EXCEPT one, masking the STORED seed too is
 *     output-identical — because the multiplier 48271 is ODD, clearing bit 31 of the
 *     state only flips bit 31 of the next product, which the return-mask `& 0x7FFFFFFF`
 *     discards, so the observable low-31 bits are unaffected. The SOLE exception is
 *     `seed == Int.MinValue` (`0x80000000` = -2147483648), a FIXED POINT of the multiply
 *     (`48271 * 2**31 ≡ 2**31 (mod 2**32)`): the faithful form keeps the seed at
 *     `0x80000000` so `next()` returns `(0x7FFFFFFF & 0x80000000) / 2**31 == 0.0`
 *     DETERMINISTICALLY forever; the mask-stored-seed mutant instead collapses the
 *     stored seed to 0 after one step, so `if (seed)` then turns false and every
 *     subsequent call diverges into the non-deterministic `Math.random()` fallback.
 *     That divergence makes the mutation OBSERVABLE at this one seed — so the faithful
 *     "mask only the return" form is mandatory, not cosmetic. Pinned by the
 *     `Random(Int.MinValue)` deterministic-zero test.)
 *   Convention (seed type): TS `seed: number` -> `Int`. roughjs seeds are always
 *     integral (`randomSeed`/`newSeed` floor a random into [0, 2^31), `Options.seed`
 *     feeds `new Random(seed)`), and the PRNG arithmetic is defined on 32-bit ints, so
 *     `Int` is the faithful and necessary representation.
 *   Idiom (JS truthiness): `if (this.seed)` is JS-number truthiness = non-zero (0 and
 *     NaN are falsy; the seed is integral here, so the predicate is exactly `!= 0`).
 *   Idiom (non-deterministic fallback, DOCUMENTED DEVIATION): when `seed == 0`,
 *     `next()` falls back to `Math.random()` — a NON-deterministic path; likewise
 *     `randomSeed()` is `Math.floor(Math.random() * 2**31)`. Both map JS `Math.random()`
 *     to a cross-platform `scala.util.Random#nextDouble()` (range [0.0, 1.0), matching
 *     `Math.random`). These unseeded paths are intentionally non-deterministic and are
 *     NOT exercised by the seeded differential test (roughjs callers pass a non-zero
 *     seed for determinism — the seeded branch is the fully deterministic, pinned one).
 *   Idiom (control flow): `if/else` expression; no `return`.
 */
package ssg
package graphs
package commons
package rough

/** roughjs RNG foundation: `randomSeed` plus the shared non-deterministic generator backing the `seed == 0` fallback path. Port of the module-level members of `math.ts`.
  */
object RoughMath {

  /** The process-global non-deterministic generator standing in for JS `Math.random()`. Used only by the unseeded fallback paths (`randomSeed` and `Random.next` when the seed is 0); never reached on
    * the seeded, deterministic path.
    */
  private val unseededGenerator: scala.util.Random = new scala.util.Random()

  /** A cross-platform analog of JS `Math.random()`: a non-deterministic Double in [0.0, 1.0). Non-deterministic by design (see the migration notes).
    */
  private[rough] def unseededRandom(): Double =
    unseededGenerator.nextDouble()

  /** Port of `randomSeed()` = `Math.floor(Math.random() * 2 ** 31)`. Returns a non-deterministic integer seed in `[0, 2147483648)` (i.e. `[0, 2^31)`).
    */
  def randomSeed(): Int =
    Math.floor(unseededRandom() * 2147483648.0).toInt
}

/** Lehmer/MINSTD pseudo-random generator. Port of `class Random` from `math.ts`.
  *
  * For any non-zero seed the sequence is fully deterministic and byte-identical to upstream roughjs across all platforms (see the migration notes on the Int-wrap equivalence to `Math.imul`). A zero
  * seed selects the non-deterministic `Math.random()` fallback.
  *
  * @param seed
  *   the 32-bit integer seed (mutated in place by each `next()` call)
  */
final class Random(private var seed: Int) {

  /** Port of `next()`. When the seed is non-zero, advances the Lehmer state and returns the next pseudo-random Double in `[0.0, 1.0)`; when the seed is zero, returns a non-deterministic
    * `Math.random()` value.
    */
  def next(): Double =
    if (seed != 0) {
      // FIRST reassign the seed to the full signed 32-bit product (sign bit retained;
      // the stored seed is deliberately NOT masked). `48271 * seed` (Int * Int) wraps
      // mod 2^32 exactly like `Math.imul(48271, seed)`.
      seed = 48271 * seed
      // THEN mask the returned value with (2**31 - 1) and divide by 2**31.
      (0x7fffffff & seed) / 2147483648.0
    } else {
      RoughMath.unseededRandom()
    }
}
