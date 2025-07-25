package org.arl.fjage.shell;

import org.jline.reader.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HistoryCompleter implements Completer {
  private final History history;

  public HistoryCompleter(History history) {
    this.history = history;
  }

  @Override
  public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    String buffer = line.line();
    Set<String> seen = new HashSet<>();

    for (History.Entry entry : history) {
      String cmd = entry.line().trim();
      if (cmd.startsWith(buffer) && seen.add(cmd)) {
        candidates.add(new Candidate(cmd));
      }
    }
  }
}