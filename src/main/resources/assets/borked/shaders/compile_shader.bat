@echo off
if "%~1"=="" (
    echo Usage: %~n0 shaderfile
    echo Example: %~n0 myshader.slang
    exit /b 1
)

set "input=%~1"
set "output=%~dpn1.spv"

echo Compiling %input% to %output%...
slangc "%input%" -profile spirv_1_6+spvGroupNonUniformBallot -o "%output%" -fvk-use-entrypoint-name -std latest 2>&1

if %errorlevel% neq 0 (
    echo slangc failed with error code %errorlevel%.
    exit /b %errorlevel%
) else (
    echo Compilation successful: %output%
)

echo Running spirv-val --scalar-block-layout --target-env vulkan1.4 "%output%"

spirv-val --scalar-block-layout --target-env vulkan1.4 "%output%"