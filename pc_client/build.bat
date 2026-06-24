@echo off
echo === Building SMS Forward PC Client ===
echo.

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist

pyinstaller --onefile --windowed --name "SMSForward" ^
    --hidden-import=pystray._win32 ^
    --hidden-import=PIL._imaging ^
    --exclude-module=test ^
    --exclude-module=unittest ^
    --exclude-module=pydoc ^
    main.py

echo.
echo === Build complete ===
echo    Output: dist\SMSForward.exe
pause
