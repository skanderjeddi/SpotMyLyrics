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

if __name__ == "__main__":
    print(get_info_linux())