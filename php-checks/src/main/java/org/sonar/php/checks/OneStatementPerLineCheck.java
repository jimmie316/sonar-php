/*
 * SonarQube PHP Plugin
 * Copyright (C) 2010 SonarSource and Akram Ben Aissi
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.php.checks;

import com.google.common.collect.Maps;
import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Grammar;
import com.sonar.sslr.squid.checks.SquidCheck;
import org.sonar.check.BelongsToProfile;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.php.lexer.PHPTagsChannel;
import org.sonar.php.parser.PHPGrammar;

import java.util.Map;

@Rule(
  key = "S122",
  priority = Priority.MAJOR)
@BelongsToProfile(title = CheckList.SONAR_WAY_PROFILE, priority = Priority.MAJOR)
public class OneStatementPerLineCheck extends SquidCheck<Grammar> {

  private final Map<Integer, StatementCount> statementsPerLine = Maps.newHashMap();
  private boolean skip = false;

  private static class StatementCount {
    int nbStatement = 0;
    int nbFunctionExpression = 0;
  }

  @Override
  public void init() {
    subscribeTo(
      PHPGrammar.TOP_STATEMENT,
      PHPGrammar.STATEMENT,
      PHPGrammar.FUNCTION_EXPRESSION);
  }


  @Override
  public void visitFile(AstNode astNode) {
    statementsPerLine.clear();
    skip = false;
  }

  @Override
  public void visitNode(AstNode node) {
    if (node.is(PHPGrammar.FUNCTION_EXPRESSION)) {
      int line = node.getTokenLine();

      if (statementsPerLine.containsKey(line)) {
        statementsPerLine.get(line).nbFunctionExpression++;
        skip = true;
      }
    } else if (!skip && !isExcluded(node)) {
      int line = node.getTokenLine();

      if (!statementsPerLine.containsKey(line)) {
        statementsPerLine.put(line, new StatementCount());
      }

      statementsPerLine.get(line).nbStatement++;
    }
  }

  @Override
  public void leaveFile(AstNode astNode) {
    for (Map.Entry<Integer, StatementCount> statementsAtLine : statementsPerLine.entrySet()) {
      StatementCount stmtCount = statementsAtLine.getValue();

      if (stmtCount.nbStatement > 1 || stmtCount.nbFunctionExpression > 1) {
        getContext().createLineViolation(this, "{0} were found on this line. Reformat the code to have only one statement per line.", statementsAtLine.getKey(),
          getMessagePrefix(stmtCount));
      }
    }
  }

  private String getMessagePrefix(StatementCount stmtCount) {
    if (stmtCount.nbFunctionExpression > 1) {
      return stmtCount.nbStatement + " statements and " + stmtCount.nbFunctionExpression + " anonymous functions";
    } else {
      return stmtCount.nbStatement + " statements";
    }
  }

  @Override
  public void leaveNode(AstNode astNode) {
    if (astNode.is(PHPGrammar.FUNCTION_EXPRESSION) && statementsPerLine.containsKey(astNode.getTokenLine())) {
      skip = false;
    }
  }

  public boolean isExcluded(AstNode statementNode) {
    AstNode child = statementNode.getFirstChild();
    return child.is(
      PHPGrammar.BLOCK,
      PHPGrammar.LABEL,
      PHPTagsChannel.INLINE_HTML,
      PHPGrammar.EMPTY_STATEMENT,
      PHPGrammar.STATEMENT);
  }
}
