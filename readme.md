map-regexps
===========

Map-regexps are like ordinary regexps, except they work on seqences of maps,
instead of on text. For example: 

```clj
(let [a {:a 1} b {:b 1}]
  (map-regexps/re-seq "{:a 1}+" [a b a a]))

> ([{:a 1}], [{:a 1} {:a 1}])
```

Please Help Me
==============

This code was written as part of a school project. Part of the requirements is
that I evaluate the usefulness of the project with real-world problems. If you
use map-regexps, please tell me (oskar.kvist@gmail.com) about what you are
using it for, so that I can evaluate it.

Usage
=====

The available metacharacters are: `[ ]`, `[^ ]`, `( )`, `|`, `+`, `*`, `?`, `.`
with their ordinary meanings. `{ }` is used to write a map literal. The whole
literal will be read by Clojure, as Clojure code. In order for a map literal L
in the pattern to match a map M in the input sequence, M must have all the
key-value pairs that L has, but M may have more. Comparison is done with `=`.
In order to match a map that has either of two values, use two literals and the
`|` metacharacter.

`( )` can be used for grouping and for saving submatches, that can be extracted
from the match. The function `re-seq-pointers` is like `re-seq` but returns
pointers into the input sequence for the beginning and end of each parenthesis.

```clj
(let [re "{:a 1}+({:b 1}){:a 1}+"
      a {:a 1}
      b {:b 1 :c 1}
      input [a a b a a]]
  (get-submatch input 1 (first (re-seq-pointers re input))))

> [{:b 1 :c 1}]
```

Note that submatches can not be used when defining the regexp itself.

`re-seq` takes a third optional argument that decides whether or not to return
the longest or shortest match (default is longest). Consider:

```clj
(let [a {:a 1}] (re-seq "{:a 1}+" [a a a]))
> ([{:a 1} {:a 1} {:a 1}])

> (let [a {:a 1}] (re-seq "{:a 1}+" [a a a] false))
([{:a 1}] [{:a 1}] [{:a 1}])
```

In the second example above, the shortest possible matches are returned.
`re-seq` lazily returns all matches, but matches can not overlap, which is why
the first example above only returns one match.

Implementation Details and Possible Improvements
================================================

The regexp engine implementation is based on [Regular Expression Matching: the
Virtual Machine Approach](http://swtch.com/~rsc/regexp/regexp2.html) by Russ
Cox.

For the problem of deciding whether or not a pattern can be matched against a
map sequence from the beginning of it, the worst-case time-complexity is O(RS),
where R is the size of the compiled regexp, and S is the input sequence's
length. However, `re-seq` restarts the search from the second map in the
seqence if it fails to find a match from the beginning.

The parser was created with [SableCC](http://sablecc.org/). The grammar used
can be found in `src/lang.grammar`. To change it, download `sablecc.jar`
(included in the SableCC zip file) and run `build_parser.bat`, or do the
equivalent on Unix-like systems.

Two possible improvements I have thought about is:
- Building the re from data instead of text.
- Allowing functions as keys in the re literals, and matching when the function
  returns true. E.g. {:a pos?} matches maps that have a positive key for :a.

If you are interested in these, let me know.

Copyright and License
=====================

Copyright (c) 2015 Oskar Kvist.
All rights reserved.
The use and distribution terms for this software are covered by the
Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
which can be found in the file epl-v10.html at the root of this distribution.
By using this software in any fashion, you are agreeing to be bound by
the terms of this license.
You must not remove this notice, or any other, from this software.
