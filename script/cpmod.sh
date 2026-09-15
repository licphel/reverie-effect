#!/bin/bash

# Auto-add MIT Copyright Header Script
# Usage: ./add_copyright.sh [directory or file]

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Copyright header template
COPYRIGHT="/*\n * MIT License\n *\n * Copyright (c) 2026 Lichphel\n *\n * Permission is hereby granted, free of charge, to any person obtaining a copy\n * of this software and associated documentation files (the \"Software\"), to deal\n * in the Software without restriction, including without limitation the rights\n * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n * copies of the Software, and to permit persons to whom the Software is\n * furnished to do so, subject to the following conditions:\n *\n * The above copyright notice and this permission notice shall be included in all\n * copies or substantial portions of the Software.\n *\n * THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\n * SOFTWARE.\n */\n\n"

# File extensions to process
EXTENSIONS=("*.c" "*.h" "*.cpp" "*.cc" "*.cxx" "*.hpp" "*.java" "*.js" "*.ts" "*.go" "*.rs" "*.py" "*.rb" "*.php" "*.swift" "*.m" "*.mm" "*.kt" "*.kts" "*.scala" "*.cs" "*.lua" "*.r" "*.pl" "*.pm" "*.t")

# Statistics
total_processed=0
total_skipped=0
total_error=0

# Show help
show_help() {
    echo "Usage: $0 [OPTIONS] [PATH]"
    echo ""
    echo "Options:"
    echo "  -h, --help     Show this help message"
    echo "  -v, --verbose  Show detailed processing info"
    echo "  -d, --dry-run  Only show files to process, don't modify"
    echo "  -q, --quiet    Suppress non-error output"
    echo ""
    echo "Path:"
    echo "  Defaults to current directory (.)"
    echo ""
    echo "Examples:"
    echo "  $0              # Process all source files in current directory"
    echo "  $0 src/         # Process all source files in src/ directory"
    echo "  $0 main.c       # Process only main.c"
    echo "  $0 -v src/      # Verbose mode processing src/"
    echo "  $0 -d .         # Dry-run: show what would be processed"
}

# Check if file already has copyright header
has_copyright() {
    local file="$1"
    # Check first 20 lines for /* or copyright keywords
    if head -n 20 "$file" 2>/dev/null | grep -qE '^[[:space:]]*/\*|Copyright|MIT License|©|All rights reserved|SPDX-License-Identifier'; then
        return 0  # Has copyright
    fi
    return 1  # No copyright
}

# Process a single file
process_file() {
    local file="$1"
    local dry_run="$2"
    local verbose="$3"
    local quiet="$4"

    # Check if file exists and is readable
    if [ ! -f "$file" ]; then
        [ "$quiet" != "true" ] && echo -e "${RED}ERROR: File does not exist - $file${NC}" >&2
        return 1
    fi

    # Check if file is writable
    if [ ! -w "$file" ]; then
        [ "$quiet" != "true" ] && echo -e "${RED}ERROR: File not writable - $file${NC}" >&2
        return 1
    fi

    # Check if file already has copyright
    if has_copyright "$file"; then
        [ "$verbose" = "true" ] && echo -e "${YELLOW}SKIPPED (has copyright): $file${NC}"
        return 2
    fi

    # Show file being processed
    if [ "$verbose" = "true" ] || [ "$dry_run" = "true" ]; then
        echo -e "${GREEN}Processing: $file${NC}"
    fi

    # If dry-run, just return
    if [ "$dry_run" = "true" ]; then
        return 0
    fi

    # Backup and add copyright header
    if ! (echo -e "$COPYRIGHT" && cat "$file") > "${file}.tmp" 2>/dev/null; then
        [ "$quiet" != "true" ] && echo -e "${RED}ERROR: Failed to create temp file - $file${NC}" >&2
        rm -f "${file}.tmp"
        return 1
    fi

    if ! mv "${file}.tmp" "$file"; then
        [ "$quiet" != "true" ] && echo -e "${RED}ERROR: Failed to replace file - $file${NC}" >&2
        rm -f "${file}.tmp"
        return 1
    fi

    [ "$quiet" != "true" ] && echo -e "${GREEN}✓ Added copyright: $file${NC}"
    return 0
}

# Main function
main() {
    local target="."
    local verbose="false"
    local dry_run="false"
    local quiet="false"

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -v|--verbose)
                verbose="true"
                shift
                ;;
            -d|--dry-run)
                dry_run="true"
                shift
                ;;
            -q|--quiet)
                quiet="true"
                shift
                ;;
            -*)
                echo "Unknown option: $1" >&2
                echo "Use -h for help" >&2
                exit 1
                ;;
            *)
                target="$1"
                shift
                ;;
        esac
    done

    # Check if target exists
    if [ ! -e "$target" ]; then
        echo -e "${RED}ERROR: Target does not exist - $target${NC}" >&2
        exit 1
    fi

    # Build find command
    local find_cmd="find \"$target\" -type f"
    local ext_pattern=""
    local first=1
    for ext in "${EXTENSIONS[@]}"; do
        if [ $first -eq 1 ]; then
            ext_pattern="-name \"$ext\""
            first=0
        else
            ext_pattern="$ext_pattern -o -name \"$ext\""
        fi
    done
    find_cmd="$find_cmd \\( $ext_pattern \\) -print"

    if [ "$quiet" != "true" ]; then
        echo "Scanning for source files..."
        echo "Target: $target"
        echo "Mode: $([ "$dry_run" = "true" ] && echo "DRY-RUN" || echo "LIVE")"
        echo ""
    fi

    # Process each file - using process substitution to avoid subshell issue
    while IFS= read -r file; do
        process_file "$file" "$dry_run" "$verbose" "$quiet"
        local result=$?
        case $result in
            0) ((total_processed++)) ;;
            2) ((total_skipped++)) ;;
            *) ((total_error++)) ;;
        esac
    done < <(eval "$find_cmd")

    # Show summary
    if [ "$quiet" != "true" ]; then
        echo ""
        echo "========================================"
        echo "Summary:"
        echo "  Processed: $total_processed"
        echo "  Skipped (has copyright): $total_skipped"
        echo "  Errors: $total_error"
        echo "========================================"

        if [ "$dry_run" = "true" ]; then
            echo "DRY-RUN: No files were actually modified."
        fi
    fi
}

# Run main function
main "$@"