# Windows PowerShell script for generating module dependency graphs
# Save this as generate-module-graphs.ps1

# Define the project root directory
$projectRoot = Get-Location

# Maximum depth for dependency traversal (increase this to show more layers)
$maxDepth = 5  # Set this to however many layers you want

# Ensure the output directory exists
New-Item -ItemType Directory -Force -Path "docs\images\graphs\" | Out-Null

# Get all modules from settings.gradle.kts
$settingsFile = "settings.gradle.kts"
$allModules = @()

if (Test-Path $settingsFile) {
    $content = Get-Content $settingsFile -Raw
    $matches = [regex]::Matches($content, 'include\("([^"]+)"\)')
    foreach ($match in $matches) {
        $allModules += $match.Groups[1].Value
    }
}

# Verify which modules actually exist in the file system
$existingModules = @()
foreach ($module in $allModules) {
    $relativePath = $module.Substring(1) -replace ":", "\"
    $fullPath = Join-Path $projectRoot $relativePath

    if (Test-Path $fullPath) {
        $existingModules += $module
        Write-Host "Found module: $module at path $fullPath"
    } else {
        Write-Host "Module path not found: $module -> $fullPath" -ForegroundColor Yellow
    }
}

# Function to extract dependencies from build.gradle.kts files
function Get-ModuleDependencies {
    param (
        [string]$moduleRelativePath,
        [array]$validModules
    )

    $fullPath = Join-Path $projectRoot $moduleRelativePath
    $buildFile = Join-Path $fullPath "build.gradle.kts"
    $dependencies = @()

    if (Test-Path $buildFile) {
        $content = Get-Content $buildFile -Raw

        # Various ways to declare project dependencies
        $patterns = @(
            'implementation\(project\("([^"]+)"\)\)',
            'api\(project\("([^"]+)"\)\)',
            'implementation\(projects\.([^\)]+)\)',
            'api\(projects\.([^\)]+)\)',
            'ksp\(project\("([^"]+)"\)\)',
            'testImplementation\(project\("([^"]+)"\)\)',
            'androidTestImplementation\(project\("([^"]+)"\)\)'
        )

        foreach ($pattern in $patterns) {
            $matches = [regex]::Matches($content, $pattern)
            foreach ($match in $matches) {
                $dep = $match.Groups[1].Value
                if ($pattern.Contains("projects\.")) {
                    $dep = $dep -replace "\.",":"
                    $dep = ":$dep"
                }

                # Only add if it's a valid module
                if ($validModules -contains $dep) {
                    $dependencies += $dep
                }
            }
        }
    }

    return $dependencies | Select-Object -Unique
}

# Create README files for modules
function Create-ReadmeFile {
    param (
        [string]$module,
        [string]$svgFileName
    )

    $relativePath = $module.Substring(1) -replace ":", "\"
    $fullPath = Join-Path $projectRoot $relativePath
    $readmePath = Join-Path $fullPath "README.md"

    # Check if README exists and directory exists
    if ((Test-Path (Split-Path $readmePath -Parent)) -and -not (Test-Path $readmePath)) {
        Write-Host "Creating README.md for $module"

        # Calculate relative path
        $depth = ($module.ToCharArray() | Where-Object { $_ -eq ':' } | Measure-Object).Count
        $relativeImagePath = "../" * $depth + "docs/images/graphs/$svgFileName"

        @"
# $module module
## Dependency graph
![$module]($relativeImagePath)
"@ | Out-File -FilePath $readmePath -Encoding utf8
    }
}

# Enhanced function to gather all dependencies up to a certain depth
function Get-AllDependencies {
    param (
        [string]$module,
        [array]$validModules,
        [int]$currentDepth = 1,
        [int]$maxDepth,
        [hashtable]$allEdges = $null,
        [hashtable]$processedEdges = $null,
        [string]$parentModule = $null
    )

    # Initialize containers if this is the first call
    if ($null -eq $allEdges) {
        $allEdges = @{}
    }
    if ($null -eq $processedEdges) {
        $processedEdges = @{}
    }

    # Stop if we've reached max depth
    if ($currentDepth > $maxDepth) {
        return $allEdges
    }

    # Get direct dependencies for this module
    $moduleRelativePath = $module.Substring(1) -replace ":", "\"
    $dependencies = Get-ModuleDependencies -moduleRelativePath $moduleRelativePath -validModules $validModules

    # Process each dependency
    foreach ($dep in $dependencies) {
        # Create an edge key
        $edgeKey = "$module -> $dep"

        # Skip if we've already processed this exact edge
        if ($processedEdges.ContainsKey($edgeKey)) {
            continue
        }

        # Mark as processed
        $processedEdges[$edgeKey] = $true

        # Determine style and color based on depth
        $style = if ($currentDepth -eq 1) { "solid" } else { "dashed" }
        $colorIntensity = 100 - (($currentDepth - 1) * 20)
        if ($colorIntensity -lt 20) { $colorIntensity = 20 }
        $color = if ($currentDepth -eq 1) { "black" } else { "gray$colorIntensity" }
        $weight = $maxDepth + 1 - $currentDepth

        # Add this edge to our collection
        $allEdges[$edgeKey] = @{
            From = $module
            To = $dep
            Depth = $currentDepth
            Style = $style
            Color = $color
            Weight = $weight
        }

        # Recursively process dependencies of this dependency
        $allEdges = Get-AllDependencies -module $dep -validModules $validModules -currentDepth ($currentDepth + 1) -maxDepth $maxDepth -allEdges $allEdges -processedEdges $processedEdges -parentModule $module
    }

    return $allEdges
}

# Main loop to generate graphs
foreach ($module in $existingModules) {
    $moduleName = $module -replace ":", "_"
    $svgFileName = "dep_graph_$moduleName.svg"
    $dotFilePath = Join-Path $env:TEMP "dep_graph_$moduleName.gv"
    $svgFilePath = Join-Path $projectRoot "docs\images\graphs\$svgFileName"

    Write-Host "Processing module: $module" -ForegroundColor Cyan

    # Create README file
    Create-ReadmeFile -module $module -svgFileName $svgFileName

    # Create dot file content - use UTF-8 without BOM
    $dotContent = "digraph G {
  rankdir=LR;
  node [shape=box, style=filled, fillcolor=lightblue];
  `"$module`" [fillcolor=lightgreen];
"

    # Get all dependencies up to the specified depth
    $allDependencies = Get-AllDependencies -module $module -validModules $existingModules -maxDepth $maxDepth

    # Add all discovered edges to the graph
    foreach ($edgeKey in $allDependencies.Keys) {
        $edge = $allDependencies[$edgeKey]
        $dotContent += "  `"$($edge.From)`" -> `"$($edge.To)`" [style=$($edge.Style), color=$($edge.Color), weight=$($edge.Weight)];`n"
    }

    # Close dot content
    $dotContent += "}"

    # Write dot file without BOM
    $utf8NoBomEncoding = New-Object System.Text.UTF8Encoding $False
    [System.IO.File]::WriteAllLines($dotFilePath, $dotContent, $utf8NoBomEncoding)

    # Generate SVG
    if (Test-Path $dotFilePath) {
        try {
            $dotProcess = Start-Process -FilePath "dot" -ArgumentList "-Tsvg", "`"$dotFilePath`"", "-o", "`"$svgFilePath`"" -PassThru -NoNewWindow -Wait

            if ($dotProcess.ExitCode -eq 0 -and (Test-Path $svgFilePath)) {
                # Optimize SVG if svgo is available
                if (Get-Command "svgo" -ErrorAction SilentlyContinue) {
                    & svgo --multipass --pretty "$svgFilePath"
                }
                Write-Host "  Generated graph at $svgFilePath" -ForegroundColor Green
            } else {
                Write-Host "  Failed to generate SVG (Exit code: $($dotProcess.ExitCode))" -ForegroundColor Red
            }
        } catch {
            Write-Host "  Error generating SVG: $_" -ForegroundColor Red
        }

        # Clean up temp file
        Remove-Item -Path $dotFilePath -Force -ErrorAction SilentlyContinue
    } else {
        Write-Host "  Failed to create dot file" -ForegroundColor Red
    }
}

Write-Host "Done! Generated dependency graphs for $($existingModules.Count) modules" -ForegroundColor Green

# Optional debugging function - uncomment to use
function Debug-Dependencies {
    param(
        [string]$module,
        [int]$maxDepth = 5
    )

    $deps = Get-AllDependencies -module $module -validModules $existingModules -maxDepth $maxDepth

    Write-Host "Dependencies for $module (max depth: $maxDepth):" -ForegroundColor Cyan

    foreach ($key in $deps.Keys) {
        $edge = $deps[$key]
        Write-Host "  Depth $($edge.Depth): $($edge.From) -> $($edge.To)" -ForegroundColor $(if ($edge.Depth -eq 1) { "Green" } else { "Gray" })
    }
}

# Uncomment this line to debug a specific module
# Debug-Dependencies -module ":your:module:here" -maxDepth 5