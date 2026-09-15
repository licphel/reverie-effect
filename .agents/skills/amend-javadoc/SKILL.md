---
name: amend-javadoc
description: Amend and fix missing or non-compliant Javadoc for Java files, supports single file or directory
disable-model-invocation: false
allowed-tools: Read,Write,Edit,Grep,Bash
argument-hint: "<java-file-or-directory>"
---

# Amend Javadoc Workflow

Amend Javadoc for each Java file in `$ARGUMENTS`, only fixing missing or non-compliant documentation while preserving existing valid Javadoc.

## Parameter Handling

- Single `.java` file: Process that file directly (including package-info)
- Directory: Recursively find all `.java` files
- Supports `.` for current directory

## Execution Steps

1. Parse `$ARGUMENTS` to determine the target file list
2. Process files in batches (max 3 per batch to avoid context overflow)
3. For each file, scan for missing or non-compliant Javadoc
4. Only add or amend Javadoc where needed, preserving valid existing documentation
5. Update source code using `Edit` or `MultiEdit`

## Amendment Strategy

### What to Check and Fix

**Missing Javadoc:**
- All public classes, interfaces, and methods MUST have Javadoc
- Protected methods should have Javadoc
- Private methods with complex logic should have Javadoc
- Package-private methods as needed
- Fields with non-obvious purpose should have Javadoc

**Non-compliant Javadoc:**
- Missing required tags (`@param`, `@return`, `@throws`)
- Incomplete parameter/return descriptions
- Mentions of implementation details instead of contract
- Incorrect grammar (non-U.S. English)
- Outdated or inaccurate documentation
- Code examples in Javadoc (remove them)
- Separator comments like `/* ====`, `/* ---` that should be deleted

**What to Preserve:**
- **Valid Javadoc that meets the standards below stays unchanged**
- Only amend Javadoc that is missing or non-compliant
- Do not rewrite valid documentation

### Detection Approach

For each element with Javadoc, validate:

1. **Presence**: Does it have Javadoc when it should?
2. **Tags**: For methods, are all `@param` tags present? Is `@return` present if non-void? Are `@throws` tags present for declared exceptions?
3. **Quality**: Is the description informative (not just "gets X")? Does it describe the contract, not implementation?
4. **Grammar**: Uses U.S. English (color, behavior, initialize)
5. **Format**: Proper tag ordering, `{@code}` usage, no code examples

### Generation Principles (for missing/amended parts)

#### Coverage

- All public classes, interfaces, and methods MUST have Javadoc
- Protected methods are recommended to have Javadoc
- Private methods only if they contain complex logic
- Private fields only if they are counterintuitive
- Package-private methods as needed
- Edit the whole file, do not skip or stop in the middle

#### Informative

- Keep Javadoc informative and concise
- One-sentence Javadoc is **ONLY** acceptable for **SIMPLE and PURE** getters/setters
- Non-trivial methods deserve full tags (`@param`, `@return`, `@throws`)
- Fields and enum constants: descriptive Javadoc required
- For interface methods, examine the primary implementation if needed to understand behavior
- Do not mention specific algorithms, field names, or internal logic
- IMPORTANT: Focus on **WHY** instead of **HOW/WHAT**

#### Format Standards

- Use U.S. English (e.g. color, behavior, initialize)
- Include all tags: `@param`, `@return`, `@throws` even for simple methods
- Use `{@code}` for code snippets
- Use `{@link ClassName#method()}` for cross-references
- **NEVER** write code examples (no `<pre>` or example code blocks)
- For field/enum Javadocs in a single line, compress the comment like `/** comment */`

#### Content Guidelines

- Document the **contract** (what it does), not implementation (how it does it)
- First sentence is a summary, ending with a period
- Parameter: `@param name description, constraints`
- Return: `@return description, note if null possible`
- Throws: `@throws someException if condition`

#### Special Cases

**package-info.java**:
- No Javadoc required

**`@Override` methods**:
- Conventionally need no Javadoc
- Only add one if the implementation differs significantly from parent contract

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

### Amendment Process

For each Java file:

1. **Parse the file** and identify all elements requiring documentation
2. **Check each element**:
    - If no Javadoc exists and one is required → Generate fresh Javadoc
    - If Javadoc exists but is non-compliant → Amend the specific parts
    - If Javadoc exists and is valid → Preserve as-is
3. **Apply amendments** using targeted edits
4. **Validate** that all issues are resolved

### Non-compliant Pattern Examples

**To amend (missing tags):**
```java
/**
 * Processes the request.
 */
public Result process(Request req) { ... }
```
→ Add `@param` and `@return` tags

**To amend (code example):**
```java
/**
 * <pre>
 *   service.process(req);
 * </pre>
 */
```
→ Remove the example

**Preserve (valid):**
```java
/**
 * Retrieves the user associated with the specified identifier.
 *
 * @param id the user identifier, must not be null
 * @return the user, or null if no user is found
 * @throws IllegalArgumentException if the id is null or empty
 */
public User getUser(String id) { ... }
```

## Example

User: `/amend-javadoc src/main/java/com/example/UserService.java`

Expected behavior:

1. Read the file
2. Identify missing or non-compliant Javadoc
3. Keep all valid existing Javadoc
4. Generate or amend only the problematic parts
5. Apply targeted edits
6. Verify all issues are resolved
7. Output completion summary listing what was changed

## Output Summary

After processing, provide:
- Total files processed
- Elements with missing Javadoc → added
- Elements with non-compliant Javadoc → amended
- Elements with valid Javadoc → preserved
- Any special cases or skipped items
```