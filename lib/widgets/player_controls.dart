import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/player_provider.dart';

class PlayerControls extends StatelessWidget {
  const PlayerControls({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Consumer<PlayerProvider>(
      builder: (context, player, _) {
        return Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: const Color(0xFF1E1E1E),
            borderRadius: BorderRadius.circular(16),
            margin: const EdgeInsets.all(16),
          ),
          child: Column(
            children: [
              // Progress bar
              Column(
                children: [
                  SliderTheme(
                    data: SliderThemeData(
                      trackHeight: 4,
                      thumbShape: const RoundSliderThumbShape(
                        enabledThumbRadius: 6,
                      ),
                    ),
                    child: Slider(
                      value: player.position.inSeconds.toDouble(),
                      max: player.duration.inSeconds.toDouble(),
                      onChanged: (value) {
                        player.seek(Duration(seconds: value.toInt()));
                      },
                      activeColor: Colors.purple,
                      inactiveColor: Colors.grey,
                    ),
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '${player.position.inMinutes}:${(player.position.inSeconds % 60).toString().padLeft(2, '0')}',
                        style: const TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                      Text(
                        '${player.duration.inMinutes}:${(player.duration.inSeconds % 60).toString().padLeft(2, '0')}',
                        style: const TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 16),
              // Control buttons
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  IconButton(
                    icon: const Icon(Icons.skip_previous),
                    onPressed: () => player.previous(),
                  ),
                  FloatingActionButton(
                    onPressed: () => player.togglePlayPause(),
                    child: Icon(player.isPlaying ? Icons.pause : Icons.play_arrow),
                  ),
                  IconButton(
                    icon: const Icon(Icons.skip_next),
                    onPressed: () => player.next(),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              // Speed control
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [0.75, 1.0, 1.25, 1.5, 2.0]
                    .map((speed) => Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          child: ElevatedButton(
                            onPressed: () => player.setPlaybackSpeed(speed),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: player.playbackSpeed == speed
                                  ? Colors.purple
                                  : Colors.grey[800],
                            ),
                            child: Text('${speed}x'),
                          ),
                        ))
                    .toList(),
              ),
            ],
          ),
        );
      },
    );
  }
}
