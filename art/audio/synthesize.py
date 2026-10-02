"""Original MChjong effects: deterministic modal synthesis, no sampled recordings.
Run with Python 3 and ffmpeg (libvorbis). The Ogg outputs are shipped resources.
"""
import math
from pathlib import Path
import random
import struct
import subprocess
import tempfile
import wave

RATE = 44100
OUTPUT = Path(__file__).resolve().parents[2] / 'common/src/main/resources/assets/mchjong/sounds/table'
# duration, resin/wood resonances, impact times, peak amplitude
SOUNDS = {
    'tile_draw': (.12, (1650, 2780, 4110), (0,), .30),
    'tile_discard': (.18, (1120, 2410, 3760), (0, .019), .38),
    'tile_call': (.25, (1280, 2530, 3900), (0, .035, .074), .35),
    'tile_kong': (.30, (740, 1620, 2900), (0, .025, .052, .087), .38),
    'riichi_stick': (.20, (1950, 3200, 4700), (0, .041), .30),
    'dice': (.48, (1800, 3100, 5200), (0, .055, .112, .174, .242, .319, .402), .28),
    'table_mechanical': (.65, (82, 164, 328), (0, .47), .32),
    'score_reveal': (.32, (880, 1320, 1760), (0,), .30),
    'grade_reveal': (.22, (1320, 1980, 2640), (0,), .20),
    'ui_accent': (.065, (1240, 2480), (0,), .17),
    'ron': (.22, (660, 990, 1320), (0,), .25),
    'tsumo': (.22, (784, 1176, 1568), (0,), .25),
    'draw_end': (.25, (220, 330, 440), (0,), .22),
    'match_end': (.48, (660, 880, 1320), (0, .13), .25),
    'countdown': (.09, (990, 1980), (0,), .20),
}


def synthesize(name, spec):
    duration, modes, impacts, peak = spec
    rng = random.Random(0x4D43484A + sum(map(ord, name)))
    musical = name in {'score_reveal', 'grade_reveal', 'ron', 'tsumo', 'draw_end', 'match_end', 'ui_accent', 'countdown'}
    samples = []
    for i in range(round(duration * RATE)):
        t = i / RATE
        value = 0.0
        for hit, start in enumerate(impacts):
            age = t - start
            if age < 0:
                continue
            decay = .075 if musical else .024
            if name == 'table_mechanical':
                decay = .12
            envelope = (1 - math.exp(-age * 2500)) * math.exp(-age / decay)
            modal = sum(math.sin(2 * math.pi * f * age) / (j + 1) for j, f in enumerate(modes))
            click = rng.uniform(-1, 1) * math.exp(-age / .003) * (0.1 if musical else .65)
            value += (modal * envelope + click) * .88 ** hit
        if name == 'table_mechanical':
            value += .18 * math.sin(2 * math.pi * (105 * t + 24 * t * t)) * math.sin(math.pi * t / duration) ** 2
        value *= min(1, (duration - t) / .015)
        samples.append(value)
    maximum = max(abs(value) for value in samples)
    return b''.join(struct.pack('<h', round(value / maximum * peak * 32767)) for value in samples)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='mchjong-audio-') as directory:
        for name, spec in SOUNDS.items():
            source = Path(directory) / (name + '.wav')
            with wave.open(str(source), 'wb') as wav:
                wav.setnchannels(1)
                wav.setsampwidth(2)
                wav.setframerate(RATE)
                wav.writeframes(synthesize(name, spec))
            subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(source),
                            '-map_metadata', '-1', '-c:a', 'libvorbis', '-q:a', '4', '-fflags', '+bitexact',
                            '-flags:a', '+bitexact', str(OUTPUT / (name + '.ogg'))], check=True)


if __name__ == '__main__':
    main()
