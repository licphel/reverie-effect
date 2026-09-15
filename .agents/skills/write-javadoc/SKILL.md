---
name: write-javadoc
description: Generate Javadoc for Java files following project standards, supports single file or directory
disable-model-invocation: false
allowed-tools: Read,Write,Edit,Grep,Bash
argument-hint: "<java-file-or-directory>"
---

# Write Javadoc Workflow

Generate or supplement Javadoc for each Java file in `$ARGUMENTS`.

## Parameter Handling

- Single `.java` file: Process that file directly (including package-info)
- Directory: Recursively find all `.java` files
- Supports `.` for current directory

## Execution Steps

1. Parse `$ARGUMENTS` to determine the target file list
2. Process files in batches (max 3 per batch to avoid context overflow)
3. For each file, follow the principles below to generate Javadoc
4. Update source code using `Edit` or `MultiEdit`

## Generation Principles

### Coverage

- All public classes, interfaces, and methods MUST have Javadoc
- Protected methods are recommended to have Javadoc
- Private methods only if they contain complex logic
- Private fields only if they are counterintuitive
- Package-private methods as needed
- Edit the whole file, do not skip or stop in the middle

### Informative

- Keep Javadoc informative and
- One-sentence Javadoc is **ONLY** acceptable for **SIMPLE and PURE** getters/setters
- Non-trivial methods deserve full tags (`@param`, `@return`, `@throws`)
- Fields and enum constants: descriptive Javadoc required
- For interface methods, examine the primary implementation if needed to understand behavior
- Do not mention specific algorithms, field names, or internal logic
- IMPORTANT: Focus on **WHY** instead of **HOW/WHAT**

### Format Standards

- Use U.S. English (e.g. color, behavior, initialize)
- Include all tags: `@param`, `@return`, `@throws` even for simple methods
- Use `{@code}` for code snippets
- Use `{@link ClassName#method()}` for cross-references
- **NEVER** write code examples (no `<pre>` or example code blocks)
- For field/enum Javadocs in a single line, compress the comment like `/** comment */`

### Content Guidelines

- Document the **contract** (what it does), not implementation (how it does it)
- First sentence is a summary, ending with a period
- Parameter: `@param name description, constraints`
- Return: `@return description, note if null possible`
- Throws: `@throws someException if condition`

### Special Cases

**package-info.java**:

- No Javadoc
-

**`@Override` methods**:

- Conventionally need no Javadoc
- Only write one if the implementation differs significantly from parent contract

**Record compact constructor**:

```java
/**
 * Creates a new {@code Point} instance.
 *
 * @param x the X coordinate
 * @param y the Y coordinate
 */
public Point { ...}
```

**Null convention**:

- All unmarked fields/parameters are treated as `@Nonnull`
- No need to explain that an argument is never `null`
- Only explain when a parameter/returned value CAN be `null`

### Existing Javadoc

- **Discard existing Javadoc entirely. Do not keep it.**
- Your judgment about its quality is irrelevant. Replace it.
- Generate fresh Javadoc following the rules above, regardless of what exists.
- For separator comments like /* ====, /* --- or others, delete them completely.

### Exemplar

See `momentum-core:io.viki.momentum.event` package. This is a small-scaled but good exemplar.

## Example

User: `/write-javadoc src/main/java/com/example/UserService.java`

Expected behavior:

1. Read the file
2. Discard old Javadocs
3. Identify classes and methods
4. (Optional) Ambiguous functionality. Ask user
5. Generate Javadoc following the standards above
6. Update the file
7. Revise your edition, and check if they meet the requirements mentioned
8. Output completion summary