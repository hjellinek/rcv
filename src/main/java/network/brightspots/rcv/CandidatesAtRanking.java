/*
 * RCTab
 * Copyright (c) 2017-2023 Bright Spots Developers.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/*
 * Purpose: Low-Memory container for a list of candidates at a single ranking.
 * Design: Optimized for the case of one candidate per ranking.
 * Conditions: Always.
 * Version history: see https://github.com/BrightSpots/rcv.
 */

package network.brightspots.rcv;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;

class CandidatesAtRanking implements Iterable<String> {
  private String[] candidateNames;

  int count() {
    return candidateNames == null ? 0 : candidateNames.length;
  }

  void addCandidate(String candidateName) {
    int n = count();
    String[] newList = new String[n + 1];
    IntStream.range(0, n).forEach(i -> newList[i] = this.candidateNames[i]);
    newList[n] = candidateName;
    this.candidateNames = newList;
  }

  String get(int i) {
    return candidateNames[i];
  }

  boolean contains(String s) {
    return Arrays.asList(this.candidateNames).contains(s);
  }

  public Iterator<String> iterator() {
    return new CandidatesAtRankingIterator();
  }

  class CandidatesAtRankingIterator implements Iterator<String> {
    private int iteratorIndex = 0;

    public boolean hasNext() {
      return iteratorIndex < candidateNames.length;
    }

    public String next() {
      if (iteratorIndex == candidateNames.length) {
        throw new NoSuchElementException();
      }

      return get(iteratorIndex++);
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
