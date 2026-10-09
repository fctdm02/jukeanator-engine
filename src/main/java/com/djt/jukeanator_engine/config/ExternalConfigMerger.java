package com.djt.jukeanator_engine.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * At startup, adds any property present in the bundled {@code application.yml} but missing from
 * the external one (e.g. {@code C:\kiosk\config\application.yml}), so a setting introduced by a
 * newer build shows up in the file an operator actually edits.
 *
 * <p>
 * This never changes behavior: the bundled file is loaded underneath the external one (see
 * {@code spring.config.additional-location} in {@code JukeANatorBackendApplication.main}), so a
 * missing key already takes its bundled value -- the merge only writes that same value out.
 *
 * <p>
 * Like {@link ExternalConfigUpdater}, edits are line-based rather than a parse/re-serialize round
 * trip, so the operator's comments, ordering and formatting are left untouched. Each missing key
 * is copied from the bundled file together with its trailing comment, the comment lines directly
 * above it, and (for a missing section) its whole block, and is placed after the nearest preceding
 * sibling that the external file already has. A key the operator has commented out (e.g.
 * {@code # winamp-exe-path: ...}) counts as present and is not re-added. The previous file is
 * saved as {@code application.yml.bak}, and nothing is written unless the merged result still
 * parses as YAML.
 */
public final class ExternalConfigMerger {

  private static final Logger LOG = LoggerFactory.getLogger(ExternalConfigMerger.class);

  private static final String CONFIG_FILE_NAME = "application.yml";
  private static final String BACKUP_SUFFIX = ".bak";

  // Group 1: indentation, group 2: key, group 3: the rest of the line after the colon.
  private static final Pattern KEY_LINE =
      Pattern.compile("^([ ]*)([^\\s#:\\-'\"][^:#]*?):(?:[ \\t]+(.*))?$");

  // A commented-out key, e.g. "  # data-dir: C:\kiosk\data". Group 1: indentation, group 2: key.
  private static final Pattern COMMENTED_KEY_LINE =
      Pattern.compile("^([ ]*)#[ \\t]*([^\\s#:\\-'\"][^:#]*?):(?:[ \\t].*)?$");

  private ExternalConfigMerger() {
  }

  /**
   * Adds every property missing from {@code configFile} (see the class comment). Failure is
   * logged rather than thrown: the bundled values still apply to any missing key, so the app can
   * start regardless.
   */
  public static void addMissingProperties(Path configFile) {

    try {
      if (!Files.exists(configFile)) {
        return;
      }

      String bundledYaml;
      try (var in = new ClassPathResource(CONFIG_FILE_NAME).getInputStream()) {
        bundledYaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      String externalYaml = Files.readString(configFile, StandardCharsets.UTF_8);

      List<String> added = new ArrayList<>();
      String merged = mergeMissingProperties(bundledYaml, externalYaml, added);
      if (added.isEmpty()) {
        return;
      }

      LoaderOptions loaderOptions = new LoaderOptions();
      loaderOptions.setAllowDuplicateKeys(false);
      new Yaml(loaderOptions).load(merged);

      Path backupFile = configFile.resolveSibling(configFile.getFileName() + BACKUP_SUFFIX);
      Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
      Files.writeString(configFile, merged, StandardCharsets.UTF_8);

      LOG.info("Added {} missing propert{} to {} (previous version saved as {}): {}",
          added.size(), added.size() == 1 ? "y" : "ies", configFile, backupFile.getFileName(),
          String.join(", ", added));

    } catch (Exception e) {
      LOG.warn("Unable to add missing properties to {}; their bundled defaults still apply: {}",
          configFile, e.getMessage(), e);
    }
  }

  /**
   * Returns {@code externalYaml} with every key from {@code bundledYaml} that it lacks inserted,
   * and appends the dotted path of each inserted key (or section) to {@code added}. Uses the
   * external file's own line separator.
   */
  static String mergeMissingProperties(String bundledYaml, String externalYaml,
      List<String> added) {

    String lineSeparator = externalYaml.contains("\r\n") ? "\r\n" : "\n";

    List<String> bundledLines = Arrays.asList(bundledYaml.split("\\R", -1));
    List<String> lines = new ArrayList<>(Arrays.asList(externalYaml.split("\\R", -1)));

    Parsed bundled = parse(bundledLines);
    Parsed external = parse(lines);

    for (Node node : bundled.nodes.values()) {

      if (external.isPresent(node.path)) {
        continue;
      }

      List<String> parentPath = node.path.subList(0, node.path.size() - 1);
      Node externalParent = null;
      if (!parentPath.isEmpty()) {
        externalParent = external.nodes.get(parentPath);
        if (externalParent == null) {
          // The parent is itself missing (it is inserted whole, children included, when its own
          // turn comes -- which is earlier, since parents precede children) or commented out.
          continue;
        }
        if (externalParent.hasValue) {
          LOG.warn("Not adding {}: {} has a value of its own in the external config, not a block",
              dotted(node.path), dotted(parentPath));
          continue;
        }
      }

      int targetIndent = childIndent(external, externalParent, parentPath,
          node.indent - (parentPath.isEmpty() ? 0 : bundled.nodes.get(parentPath).indent));
      List<String> block = reindent(
          bundledLines.subList(node.leadingStart, node.end), targetIndent - node.indent);

      boolean topLevel = parentPath.isEmpty();
      int insertAt = -1;
      boolean beforeNextSibling = false;

      List<Node> siblings = bundled.children(parentPath);
      int position = siblings.indexOf(node);
      for (int i = position - 1; i >= 0 && insertAt < 0; i--) {
        Node present = external.nodes.get(siblings.get(i).path);
        if (present != null) {
          insertAt = present.end;
        }
      }
      for (int i = position + 1; i < siblings.size() && insertAt < 0; i++) {
        Node present = external.nodes.get(siblings.get(i).path);
        if (present != null) {
          insertAt = present.leadingStart;
          beforeNextSibling = true;
        }
      }
      if (insertAt < 0) {
        insertAt = externalParent != null ? externalParent.end : endOfContent(lines);
      }

      List<String> insertion = new ArrayList<>(block);
      if (topLevel) {
        // Keep top-level sections separated by a blank line, as in the bundled file.
        if (beforeNextSibling) {
          insertion.add("");
        } else if (insertAt > 0) {
          insertion.add(0, "");
        }
      }

      lines.addAll(insertAt, insertion);
      added.add(dotted(node.path));
      external = parse(lines);
    }

    return String.join(lineSeparator, lines);
  }

  /**
   * The indentation a new child of {@code externalParent} should use: that of an existing child
   * if it has one, otherwise the parent's own indentation plus the bundled file's step.
   */
  private static int childIndent(Parsed external, Node externalParent, List<String> parentPath,
      int bundledStep) {

    if (externalParent == null) {
      return 0;
    }
    List<Node> existingChildren = external.children(parentPath);
    return existingChildren.isEmpty() ? externalParent.indent + bundledStep
        : existingChildren.get(0).indent;
  }

  private static List<String> reindent(List<String> block, int delta) {

    List<String> result = new ArrayList<>();
    for (String line : block) {
      if (line.isBlank() || delta == 0) {
        result.add(line);
      } else if (delta > 0) {
        result.add(" ".repeat(delta) + line);
      } else {
        result.add(line.substring(Math.min(-delta, indentOf(line))));
      }
    }
    return result;
  }

  // Index just past the last non-blank line, so content added at the end of the file goes before
  // any trailing newline/blank lines.
  private static int endOfContent(List<String> lines) {

    int end = lines.size();
    while (end > 0 && lines.get(end - 1).isBlank()) {
      end--;
    }
    return end;
  }

  private static int indentOf(String line) {

    int indent = 0;
    while (indent < line.length() && line.charAt(indent) == ' ') {
      indent++;
    }
    return indent;
  }

  private static String dotted(List<String> path) {
    return String.join(".", path);
  }

  private static boolean isComment(String line) {
    return line.stripLeading().startsWith("#");
  }

  // ── Parsing ───────────────────────────────────────────────────────────────

  /** A mapping key and the lines that belong to it. */
  private static final class Node {

    List<String> path;
    int indent;
    int keyLine;
    int leadingStart; // first of the comment lines directly above the key (or keyLine if none)
    int end; // exclusive: one past the key's last descendant line
    boolean hasValue; // true for "key: value", false for "key:" opening a nested block
  }

  private static final class Parsed {

    final Map<List<String>, Node> nodes = new LinkedHashMap<>(); // document order
    final Set<List<String>> commentedOut = new HashSet<>();

    boolean isPresent(List<String> path) {
      return nodes.containsKey(path) || commentedOut.contains(path);
    }

    List<Node> children(List<String> parentPath) {

      List<Node> children = new ArrayList<>();
      for (Node node : nodes.values()) {
        if (node.path.size() == parentPath.size() + 1
            && node.path.subList(0, parentPath.size()).equals(parentPath)) {
          children.add(node);
        }
      }
      return children;
    }
  }

  private static Parsed parse(List<String> lines) {

    Parsed parsed = new Parsed();
    List<Node> stack = new ArrayList<>();

    for (int i = 0; i < lines.size(); i++) {

      String line = lines.get(i);
      if (line.isBlank()) {
        continue;
      }

      if (isComment(line)) {
        Matcher commented = COMMENTED_KEY_LINE.matcher(line);
        if (commented.matches()) {
          int indent = commented.group(1).length();
          parsed.commentedOut.add(childPath(stack, indent, commented.group(2).strip()));
        }
        continue;
      }

      Matcher key = KEY_LINE.matcher(line);
      if (!key.matches() || line.stripLeading().startsWith("- ")) {
        continue; // a list item or other non-key content -- part of its parent's block
      }

      Node node = new Node();
      node.indent = key.group(1).length();
      node.path = childPath(stack, node.indent, key.group(2).strip());
      node.keyLine = i;
      String rest = key.group(3) == null ? "" : key.group(3).strip();
      node.hasValue = !rest.isEmpty() && !rest.startsWith("#");

      node.leadingStart = i;
      while (node.leadingStart > 0 && isComment(lines.get(node.leadingStart - 1))
          && indentOf(lines.get(node.leadingStart - 1)) == node.indent) {
        node.leadingStart--;
      }

      while (!stack.isEmpty() && stack.get(stack.size() - 1).indent >= node.indent) {
        stack.remove(stack.size() - 1);
      }
      stack.add(node);
      parsed.nodes.put(node.path, node);
    }

    for (Node node : parsed.nodes.values()) {
      int lastContent = node.keyLine;
      for (int j = node.keyLine + 1; j < lines.size(); j++) {
        String line = lines.get(j);
        if (line.isBlank() || isComment(line)) {
          continue;
        }
        if (indentOf(line) <= node.indent) {
          break;
        }
        lastContent = j;
      }
      node.end = lastContent + 1;
    }

    return parsed;
  }

  // The path of a key at the given indentation, given the stack of currently open keys.
  private static List<String> childPath(List<Node> stack, int indent, String key) {

    List<String> path = new ArrayList<>();
    for (Node open : stack) {
      if (open.indent < indent) {
        path = open.path;
      }
    }
    List<String> result = new ArrayList<>(path);
    result.add(key);
    return List.copyOf(result);
  }
}
