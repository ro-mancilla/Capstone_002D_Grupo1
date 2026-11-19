#!/bin/bash
# Save this as generate-module-graphs.sh

# Check if the dot command is available
if ! command -v dot &> /dev/null
then
    echo "The 'dot' command is not found. This is required to generate SVGs from the Graphviz files."
    echo "Installation instructions:"
    echo "  - On Arch Linux: sudo pacman -S graphviz"
    exit 1
fi

# Check if the svgo command is available
if ! command -v svgo &> /dev/null
then
    echo "The 'svgo' command is not found. This is required to cleanup and compress SVGs."
    echo "Installation instructions:"
    echo "  - On Arch Linux: sudo npm install -g svgo (after installing nodejs and npm)"
    exit 1
fi

# Define the project root directory
PROJECT_ROOT="$(pwd)"

# Maximum depth for dependency traversal
MAX_DEPTH=5

# Initialize an array to store excluded modules
excluded_modules=()

# Parse command-line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --exclude-module)
            excluded_modules+=("$2")
            shift
            shift
            ;;
        --max-depth)
            MAX_DEPTH="$2"
            shift
            shift
            ;;
        *)
            echo "Unknown parameter passed: $1"
            echo "Usage: $0 [--exclude-module <module>] [--max-depth <depth>]"
            exit 1
            ;;
    esac
done

# Get all modules from settings.gradle.kts using standard regex
SETTINGS_FILE="settings.gradle.kts"
declare -a ALL_MODULES=()
declare -a EXISTING_MODULES=()

if [[ -f "$SETTINGS_FILE" ]]; then
    # Extract module names using sed instead of grep -P
    module_paths=$(sed -n 's/.*include("\([^"]*\)").*/\1/p' "$SETTINGS_FILE")

    while IFS= read -r module; do
        [[ -z "$module" ]] && continue
        ALL_MODULES+=("$module")
    done <<< "$module_paths"
fi

# Verify which modules actually exist in the file system
for module in "${ALL_MODULES[@]}"; do
    # Check if module is excluded
    if [[ " ${excluded_modules[@]} " =~ " ${module} " ]]; then
        echo "Excluding module: $module"
        continue
    fi

    RELATIVE_PATH="${module:1}"
    RELATIVE_PATH="${RELATIVE_PATH//:/'/'}"
    FULL_PATH="$PROJECT_ROOT/$RELATIVE_PATH"

    if [[ -d "$FULL_PATH" ]]; then
        EXISTING_MODULES+=("$module")
        echo "Found module: $module at path $FULL_PATH"
    else
        echo -e "\033[33mModule path not found: $module -> $FULL_PATH\033[0m"
    fi
done

# Ensure the output directory exists
mkdir -p "docs/images/graphs/"

# Function to extract dependencies from build.gradle.kts files
get_module_dependencies() {
    local module_relative_path="$1"
    local full_path="$PROJECT_ROOT/$module_relative_path"
    local build_file="$full_path/build.gradle.kts"

    if [[ ! -f "$build_file" ]]; then
        return
    fi

    # Extract project dependencies using sed
    sed -n 's/.*\(implementation\|api\|ksp\|testImplementation\|androidTestImplementation\)(project("\([^"]*\)".*/\2/p' "$build_file" 2>/dev/null || true

    # Extract projects.xxx style dependencies and convert to :xxx format
    sed -n 's/.*\(implementation\|api\)(projects\.\([^)]*\)).*/\2/p' "$build_file" 2>/dev/null | sed 's/\./:/g; s/^/:/' || true
}

# Create README files for modules
create_readme_file() {
    local module="$1"
    local svg_file_name="$2"
    local relative_path="${module:1}"
    relative_path="${relative_path//:/'/'}"
    local full_path="$PROJECT_ROOT/$relative_path"
    local readme_path="$full_path/README.md"

    if [[ -d "$(dirname "$readme_path")" ]] && [[ ! -f "$readme_path" ]]; then
        echo "Creating README.md for $module"

        # Calculate relative path depth
        local depth=$(grep -o ":" <<< "$module" | wc -l)
        local relative_image_path=""
        for ((i=0; i<depth; i++)); do
            relative_image_path+="../"
        done
        relative_image_path+="docs/images/graphs/$svg_file_name"

        cat > "$readme_path" << EOF
# $module module
## Dependency graph
![$module]($relative_image_path)
EOF
    fi
}

# Function to collect all dependencies recursively with depth tracking
collect_dependencies_recursive() {
    local module="$1"
    local current_depth="$2"
    local visited_key="$3"

    # Stop if max depth reached
    if ((current_depth > MAX_DEPTH)); then
        return
    fi

    # Get module path
    local module_relative_path="${module:1}"
    module_relative_path="${module_relative_path//:/'/'}"

    # Get direct dependencies
    local deps
    deps=$(get_module_dependencies "$module_relative_path")

    # Process each dependency
    while IFS= read -r dep; do
        [[ -z "$dep" ]] && continue

        # Validate dependency exists in our module list
        local dep_exists=false
        for existing_mod in "${EXISTING_MODULES[@]}"; do
            if [[ "$existing_mod" == "$dep" ]]; then
                dep_exists=true
                break
            fi
        done

        [[ "$dep_exists" == false ]] && continue

        local edge_key="$module -> $dep"

        # Skip if already processed this edge
        if [[ "$visited_key" == *"|$edge_key|"* ]]; then
            continue
        fi

        # Add to visited
        visited_key+="|$edge_key|"

        # Determine style and color
        local style="solid"
        local color="black"
        local weight=$((MAX_DEPTH + 1 - current_depth))

        if ((current_depth > 1)); then
            style="dashed"
            local color_intensity=$((100 - (current_depth - 1) * 20))
            ((color_intensity < 20)) && color_intensity=20
            color="gray$color_intensity"
        fi

        # Output edge
        echo "$module|$dep|$style|$color|$weight"

        # Recurse into dependency
        collect_dependencies_recursive "$dep" $((current_depth + 1)) "$visited_key"

    done <<< "$deps"
}

# Main loop to generate graphs
for module in "${EXISTING_MODULES[@]}"; do
    MODULE_NAME="${module//:/_}"
    MODULE_NAME="${MODULE_NAME//-/_}"
    SVG_FILE_NAME="dep_graph_${MODULE_NAME}.svg"
    DOT_FILE_PATH="/tmp/dep_graph_${MODULE_NAME}.gv"
    SVG_FILE_PATH="$PROJECT_ROOT/docs/images/graphs/$SVG_FILE_NAME"

    echo -e "\033[36mProcessing module: $module\033[0m"

    # Create README file
    create_readme_file "$module" "$SVG_FILE_NAME"

    # Create dot file header
    cat > "$DOT_FILE_PATH" << EOF
digraph G {
  rankdir=LR;
  node [shape=box, style=filled, fillcolor=lightblue];
  "$module" [fillcolor=lightgreen];
EOF

    # Collect all dependencies and write edges
    edges=$(collect_dependencies_recursive "$module" 1 "||")

    # Remove duplicates and add to dot file
    echo "$edges" | sort -u | while IFS='|' read -r from_module to_module style color weight; do
        [[ -z "$from_module" || -z "$to_module" ]] && continue
        echo "  \"$from_module\" -> \"$to_module\" [style=$style, color=$color, weight=$weight];" >> "$DOT_FILE_PATH"
    done

    # Close dot file
    echo "}" >> "$DOT_FILE_PATH"

    # Generate SVG
    if [[ -f "$DOT_FILE_PATH" ]]; then
        if dot -Tsvg "$DOT_FILE_PATH" | svgo --multipass --pretty --output="$SVG_FILE_PATH" - 2>/dev/null; then
            echo -e "  \033[32mGenerated graph at $SVG_FILE_PATH\033[0m"
        else
            echo -e "  \033[31mFailed to generate SVG\033[0m"
        fi

        # Clean up temp file
        rm -f "$DOT_FILE_PATH"
    else
        echo -e "  \033[31mFailed to create dot file\033[0m"
    fi
done

echo -e "\033[32mDone! Generated dependency graphs for ${#EXISTING_MODULES[@]} modules\033[0m"
