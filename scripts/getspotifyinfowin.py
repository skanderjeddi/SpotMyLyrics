import sys

def get_info_windows():
    import win32gui
    windows = []
    old_window = win32gui.FindWindow("SpotifyMainWindow", None)
    old = win32gui.GetWindowText(old_window)
    def find_spotify_uwp(hwnd, windows):
        text = win32gui.GetWindowText(hwnd)
        classname = win32gui.GetClassName(hwnd)
        if classname == "Chrome_WidgetWin_0" and len(text) > 0:
            windows.append(text)
    if old:
        windows.append(old)
    else:
        win32gui.EnumWindows(find_spotify_uwp, windows)
    if len(windows) == 0:
        return "NOT RUNNING"
    try:
        artist, track = windows[0].split(" - ", 1)
    except ValueError:
        artist = ''
        track = windows[0]
    if windows[0].startswith('Spotify'):
        return "PAUSED"
    return artist, track

if __name__ == "__main__":
    print(get_info_windows())