import sys

def get_info_linux():
    import dbus
    if not hasattr(get_info_linux, 'session_bus'):
        get_info_linux.session_bus = dbus.SessionBus()
    try:
        spotify_bus = get_info_linux.session_bus.get_object("org.mpris.MediaPlayer2.spotify", "/org/mpris/MediaPlayer2")
        spotify_properties = dbus.Interface(spotify_bus, "org.freedesktop.DBus.Properties")
        metadata = spotify_properties.Get("org.mpris.MediaPlayer2.Player", "Metadata")
    except dbus.exceptions.DBusException:
        return "CLOSED"
    track = str(metadata['xesam:title'])
    try:
        artist = str(metadata['xesam:artist'][0])
    except IndexError:
        return "CLOSED"
    status = str(spotify_properties.Get("org.mpris.MediaPlayer2.Player", "PlaybackStatus"))
    if status.lower() != 'playing':
        return "PAUSED"
    return artist, track

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
    if len(sys.argv) == 2:
        if sys.argv[1] == "win":
            print(get_info_windows())
        elif sys.argv[2] == "linux":
            print(get_info_linux())
    else:
        print("MISSING OS ARGUMENT")