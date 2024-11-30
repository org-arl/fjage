/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

package org.arl.fjage.shell;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.arl.fjage.*;
import org.arl.fjage.param.*;

public class Documentation {

  protected List<String> doc = new ArrayList<String>();
  protected int staticSize = 0;

  protected static final Pattern heading = Pattern.compile("^#+ +([^ ]+) +-.*$");
  protected static final Pattern section = Pattern.compile("^#+ (.*)$");
  protected static final String crlf = "\\r?\\n";
  protected static final String header = "^#+ +";
  protected static final String gaps = "\\n+$";
  protected static final String placeholder = "@@";    // to be replaced by agent name

  /**
   * Add markdown documentation.
   *
   * @param s multiline string.
   */
  public void add(String s) {
    if (doc.size() > staticSize) doc.subList(staticSize, doc.size()).clear();
    String[] lines = s.split(crlf);
    for (String line: lines)
      doc.add(line);
    staticSize = doc.size();
  }

  /**
   * Get documentation index.
   */
  public String get(Agent agent) {
    build(agent);
    List<String> topics = new ArrayList<String>();
    for (String s: doc)
      if (s.startsWith("# ")) topics.add(s.substring(2));
    topics.sort(null);
    StringBuilder sb = new StringBuilder();
    for (String s: topics) {
      sb.append(s);
      sb.append('\n');
    }
    return sb.toString();
  }

  /**
   * Get documentation by keyword.
   *
   * @param keyword keyword.
   */
  public String get(Agent agent, String keyword) {
    build(agent);
    int count = 0;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < doc.size(); i++) {
      String s = doc.get(i);
      Matcher m = heading.matcher(s);
      if ((m.matches() && m.group(1).equals(keyword))) {
        extract(sb, i);
        count++;
        continue;
      }
      m = section.matcher(s);
      if (m.matches() && m.group(1).equals(keyword)) {
        extract(sb, i);
        count++;
        continue;
      }
    }
    if (count == 0) search(sb, keyword);
    return sb.toString().replaceAll(gaps, "\n");
  }

  /**
   * Search documentation topics by keyword.
   *
   * @param sb string builder.
   * @param keyword keyword.
   */
  protected void search(StringBuilder sb, String keyword) {
    keyword = keyword.toLowerCase();
    Set<String> topics = new HashSet<String>();
    String topic = null;
    for (String s: doc) {
      Matcher m = section.matcher(s);
      if (m.matches()) topic = s.replaceAll(header, "- ");
      if (s.toLowerCase().contains(keyword)) topics.add(topic);
    }
    if (topics.size() == 0) return;
    sb.append("Possible topics:\n");
    for (String s: topics) {
      sb.append(s);
      sb.append('\n');
    }
  }

  /**
   * Extract documentation.
   *
   * @param sb string builder.
   * @param pos position.
   */
  protected void extract(StringBuilder sb, int pos) {
    String s = doc.get(pos);
    int level = s.indexOf(' ');
    if (level <= 0) return;
    int endlevel = level;
    if (s.startsWith("# ")) sb.append(s);
    else sb.append(s.replaceAll(header, ""));
    sb.append('\n');
    int skip = 0;
    for (int i = pos+1; i < doc.size(); i++) {
      s = doc.get(i);
      Matcher m = section.matcher(s);
      if (m.matches()) {
        m = heading.matcher(s);
        level = s.indexOf(' ');
        if (level <= endlevel) break;
        boolean nl = false;
        if (skip == 0 || level <= skip) {
          if (skip > 0) nl = true;
          skip = 0;
          s = s.replaceAll(header, "");
        }
        if (m.matches()) {
          skip = level;
          sb.append("* ");
          sb.append(s);
          sb.append('\n');
        } else if (nl) {
          sb.append('\n');
        }
      }
      if (skip == 0) {
        sb.append(s);
        sb.append('\n');
      }
    }
    sb.append('\n');
  }

  /**
   * Build dynamic documentation by querying agents.
   *
   * @param agent agent.
   */
  protected void build(Agent agent) {
    if (agent == null) return;
    if (doc.size() > staticSize) doc.subList(staticSize, doc.size()).clear();
    Container c = agent.getContainer();
    AgentID[] agentIDs = c.getAgents();
    for (AgentID a: agentIDs) {
      ParameterReq req = new ParameterReq();
      req.setRecipient(a);
      req.get(new NamedParameter("__doc__"));
      Message rsp = agent.request(req, 1000);
      if (rsp != null && rsp instanceof ParameterRsp) {
        Object docstr = ((ParameterRsp)rsp).get(new NamedParameter("__doc__"));
        if (docstr != null) {
          String s = docstr.toString().replaceAll(placeholder, a.getName());
          String[] lines = s.split(crlf);
          for (String line: lines)
            doc.add(line);
        }
      }
    }
  }

}
