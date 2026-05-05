import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/player_provider.dart';

class SongList extends StatelessWidget {
  final List<dynamic> songs;

  const SongList({Key? key, required this.songs}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      itemCount: songs.length,
      itemBuilder: (context, index) {
        final song = songs[index];
        return Consumer<PlayerProvider>(
          builder: (context, player, _) {
            final isCurrentSong = player.currentSong?.id == song.id;
            return ListTile(
              leading: isCurrentSong
                  ? const Icon(Icons.music_note, color: Colors.purple)
                  : const Icon(Icons.music_note, color: Colors.grey),
              title: Text(
                song.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontWeight: isCurrentSong ? FontWeight.bold : FontWeight.normal,
                  color: isCurrentSong ? Colors.purple : Colors.white,
                ),
              ),
              subtitle: Text(
                song.artist,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              trailing: Text(
                '${song.duration.inMinutes}:${(song.duration.inSeconds % 60).toString().padLeft(2, '0')}',
                style: const TextStyle(color: Colors.grey),
              ),
              onTap: () {
                context.read<PlayerProvider>().playSong(song);
              },
            );
          },
        );
      },
    );
  }
}
