#!/usr/bin/env python3
"""Synchronize and validate the shared unofficial-port icon badge family."""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
import zlib
from pathlib import Path


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
REPO_ROOT = Path(__file__).resolve().parents[2]
MANIFEST_PATH = REPO_ROOT / "artwork" / "port-icons" / "manifest.json"


class ValidationError(RuntimeError):
    pass


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def png_chunk(kind: bytes, payload: bytes) -> bytes:
    crc = zlib.crc32(kind)
    crc = zlib.crc32(payload, crc) & 0xFFFFFFFF
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", crc)


def parse_chunks(data: bytes) -> list[tuple[str, bytes]]:
    if not data.startswith(PNG_SIGNATURE):
        raise ValidationError("invalid PNG signature")

    chunks: list[tuple[str, bytes]] = []
    offset = len(PNG_SIGNATURE)
    while offset < len(data):
        if offset + 12 > len(data):
            raise ValidationError("truncated PNG chunk header")
        length = struct.unpack_from(">I", data, offset)[0]
        type_bytes = data[offset + 4 : offset + 8]
        end = offset + 12 + length
        if end > len(data):
            raise ValidationError("truncated PNG chunk payload")
        payload = data[offset + 8 : offset + 8 + length]
        expected_crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        actual_crc = zlib.crc32(type_bytes)
        actual_crc = zlib.crc32(payload, actual_crc) & 0xFFFFFFFF
        if actual_crc != expected_crc:
            kind = type_bytes.decode("ascii", errors="replace")
            raise ValidationError(f"bad CRC in {kind} chunk")
        kind = type_bytes.decode("ascii")
        chunks.append((kind, payload))
        offset = end
        if kind == "IEND":
            if offset != len(data):
                raise ValidationError("trailing bytes after IEND")
            break

    if not chunks or chunks[-1][0] != "IEND":
        raise ValidationError("missing IEND chunk")
    return chunks


def paeth(left: int, up: int, upper_left: int) -> int:
    estimate = left + up - upper_left
    left_distance = abs(estimate - left)
    up_distance = abs(estimate - up)
    diagonal_distance = abs(estimate - upper_left)
    if left_distance <= up_distance and left_distance <= diagonal_distance:
        return left
    if up_distance <= diagonal_distance:
        return up
    return upper_left


def decode_rgba(chunks: list[tuple[str, bytes]], width: int, height: int) -> bytes:
    compressed = b"".join(payload for kind, payload in chunks if kind == "IDAT")
    raw = zlib.decompress(compressed)
    stride = width * 4
    expected_length = height * (stride + 1)
    if len(raw) != expected_length:
        raise ValidationError(
            f"unexpected decoded byte count: {len(raw)} != {expected_length}"
        )

    decoded = bytearray(height * stride)
    source_offset = 0
    for y in range(height):
        filter_type = raw[source_offset]
        source_offset += 1
        scanline = raw[source_offset : source_offset + stride]
        source_offset += stride
        row_offset = y * stride
        previous_offset = (y - 1) * stride

        for x, value in enumerate(scanline):
            left = decoded[row_offset + x - 4] if x >= 4 else 0
            up = decoded[previous_offset + x] if y > 0 else 0
            upper_left = decoded[previous_offset + x - 4] if y > 0 and x >= 4 else 0
            if filter_type == 0:
                predictor = 0
            elif filter_type == 1:
                predictor = left
            elif filter_type == 2:
                predictor = up
            elif filter_type == 3:
                predictor = (left + up) // 2
            elif filter_type == 4:
                predictor = paeth(left, up, upper_left)
            else:
                raise ValidationError(f"unsupported PNG filter type {filter_type}")
            decoded[row_offset + x] = (value + predictor) & 0xFF
    return bytes(decoded)


def encode_rgba(width: int, height: int, rgba: bytes, png_rules: dict) -> bytes:
    stride = width * 4
    if len(rgba) != height * stride:
        raise ValidationError("RGBA buffer length does not match its dimensions")
    filtered = bytearray()
    for y in range(height):
        filtered.append(0)
        start = y * stride
        filtered.extend(rgba[start : start + stride])
    chunks = [
        png_chunk(
            b"IHDR",
            struct.pack(
                ">IIBBBBB",
                width,
                height,
                png_rules["bit_depth"],
                png_rules["color_type"],
                0,
                0,
                png_rules["interlace"],
            ),
        ),
        png_chunk(b"sRGB", bytes([png_rules["srgb_intent"]])),
        png_chunk(b"gAMA", struct.pack(">I", png_rules["gamma"])),
        png_chunk(b"pHYs", struct.pack(">IIB", *png_rules["pixels_per_meter"])),
        png_chunk(b"IDAT", zlib.compress(bytes(filtered), level=9)),
        png_chunk(b"IEND", b""),
    ]
    return PNG_SIGNATURE + b"".join(chunks)


def read_rgba(path: Path, expected_sha: str | None = None) -> tuple[bytes, int, int, bytes]:
    if not path.is_file():
        raise ValidationError(f"missing file: {path}")
    data = path.read_bytes()
    if expected_sha is not None and sha256(data) != expected_sha.upper():
        raise ValidationError(f"{path}: source SHA-256 does not match its manifest pin")
    chunks = parse_chunks(data)
    width, height, bit_depth, color_type, compression, filtering, interlace = struct.unpack(
        ">IIBBBBB", chunks[0][1]
    )
    if (bit_depth, color_type, compression, filtering, interlace) != (8, 6, 0, 0, 0):
        raise ValidationError(f"{path}: source must be non-interlaced 8-bit RGBA PNG")
    return data, width, height, decode_rgba(chunks, width, height)


def generate_from_sources(icon: dict, png_rules: dict) -> bytes:
    source = icon["generated_from"]
    _, width, height, base_rgba = read_rgba(
        REPO_ROOT / source["base"], source["base_sha256"]
    )
    _, overlay_width, overlay_height, overlay_rgba = read_rgba(
        REPO_ROOT / source["overlay"], source["overlay_sha256"]
    )
    if (width, height) != (icon["width"], icon["height"]):
        raise ValidationError(f"{icon['id']}: base dimensions do not match the manifest")

    region = icon["badge_region"]
    x, y = region["x"], region["y"]
    if (overlay_width, overlay_height) != (region["width"], region["height"]):
        raise ValidationError(f"{icon['id']}: overlay dimensions do not match badge region")

    composed = bytearray(base_rgba)
    for overlay_y in range(overlay_height):
        for overlay_x in range(overlay_width):
            overlay_offset = (overlay_y * overlay_width + overlay_x) * 4
            target_offset = ((y + overlay_y) * width + x + overlay_x) * 4
            alpha = overlay_rgba[overlay_offset + 3]
            if alpha == 0:
                continue
            if alpha == 255:
                composed[target_offset : target_offset + 4] = overlay_rgba[
                    overlay_offset : overlay_offset + 4
                ]
                continue
            inverse = 255 - alpha
            for channel in range(3):
                foreground = overlay_rgba[overlay_offset + channel]
                background = composed[target_offset + channel]
                composed[target_offset + channel] = (
                    foreground * alpha + background * inverse + 127
                ) // 255
            background_alpha = composed[target_offset + 3]
            composed[target_offset + 3] = alpha + (
                background_alpha * inverse + 127
            ) // 255

    changed: list[tuple[int, int]] = []
    for pixel in range(width * height):
        offset = pixel * 4
        if composed[offset : offset + 4] != base_rgba[offset : offset + 4]:
            changed.append((pixel % width, pixel // width))
    if not changed:
        raise ValidationError(f"{icon['id']}: generated badge changed no pixels")
    actual_bbox = [
        min(point[0] for point in changed),
        min(point[1] for point in changed),
        max(point[0] for point in changed),
        max(point[1] for point in changed),
    ]
    diff_rules = icon["diff_from_base"]
    if len(changed) != diff_rules["changed_pixels"]:
        raise ValidationError(
            f"{icon['id']}: changed pixel count {len(changed)} != "
            f"{diff_rules['changed_pixels']}"
        )
    if actual_bbox != diff_rules["bbox"]:
        raise ValidationError(
            f"{icon['id']}: changed-pixel bbox {actual_bbox} != {diff_rules['bbox']}"
        )
    if any(
        point_x < x
        or point_y < y
        or point_x >= x + overlay_width
        or point_y >= y + overlay_height
        for point_x, point_y in changed
    ):
        raise ValidationError(f"{icon['id']}: generated pixels escaped badge region")
    return encode_rgba(width, height, bytes(composed), png_rules)


def validate_png(path: Path, icon: dict, png_rules: dict) -> bytes:
    if not path.is_file():
        raise ValidationError(f"missing file: {path}")
    data = path.read_bytes()
    actual_sha = sha256(data)
    expected_sha = icon["sha256"].upper()
    if actual_sha != expected_sha:
        raise ValidationError(f"{path}: SHA-256 {actual_sha} != {expected_sha}")

    chunks = parse_chunks(data)
    kinds = [kind for kind, _ in chunks]
    if kinds[0] != "IHDR" or kinds[-1] != "IEND":
        raise ValidationError(f"{path}: IHDR/IEND ordering is invalid")
    for required in png_rules["required_chunks"]:
        if required not in kinds:
            raise ValidationError(f"{path}: missing required {required} chunk")
    for forbidden in png_rules["forbidden_chunks"]:
        if forbidden in kinds:
            raise ValidationError(f"{path}: forbidden {forbidden} chunk is present")

    payloads: dict[str, list[bytes]] = {}
    for kind, payload in chunks:
        payloads.setdefault(kind, []).append(payload)
    if len(payloads["IHDR"]) != 1:
        raise ValidationError(f"{path}: expected exactly one IHDR chunk")

    width, height, bit_depth, color_type, compression, filtering, interlace = struct.unpack(
        ">IIBBBBB", payloads["IHDR"][0]
    )
    expected_dimensions = (icon["width"], icon["height"])
    if (width, height) != expected_dimensions:
        raise ValidationError(
            f"{path}: dimensions {(width, height)} != {expected_dimensions}"
        )
    actual_format = (bit_depth, color_type, compression, filtering, interlace)
    expected_format = (
        png_rules["bit_depth"],
        png_rules["color_type"],
        0,
        0,
        png_rules["interlace"],
    )
    if actual_format != expected_format:
        raise ValidationError(f"{path}: PNG format {actual_format} != {expected_format}")

    exact_metadata = {
        "sRGB": bytes([png_rules["srgb_intent"]]),
        "gAMA": struct.pack(">I", png_rules["gamma"]),
        "pHYs": struct.pack(">IIB", *png_rules["pixels_per_meter"]),
    }
    for kind, expected in exact_metadata.items():
        values = payloads.get(kind, [])
        if values != [expected]:
            raise ValidationError(f"{path}: unexpected {kind} metadata")

    rgba = decode_rgba(chunks, width, height)
    region = icon["badge_region"]
    x, y, region_width, region_height = (
        region["x"],
        region["y"],
        region["width"],
        region["height"],
    )
    if x < 0 or y < 0 or x + region_width > width or y + region_height > height:
        raise ValidationError(f"{path}: badge region falls outside the image")
    region_bytes = bytearray()
    stride = width * 4
    for row in range(y, y + region_height):
        start = row * stride + x * 4
        region_bytes.extend(rgba[start : start + region_width * 4])
    region_sha = sha256(bytes(region_bytes))
    expected_region_sha = region["rgba_sha256"].upper()
    if region_sha != expected_region_sha:
        raise ValidationError(
            f"{path}: badge-region SHA-256 {region_sha} != {expected_region_sha}"
        )
    return data


def load_manifest() -> dict:
    try:
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValidationError(f"cannot read {MANIFEST_PATH}: {exc}") from exc
    if manifest.get("schema_version") != 1:
        raise ValidationError("unsupported icon manifest schema")
    return manifest


def run(sync: bool) -> None:
    manifest = load_manifest()
    png_rules = manifest["png"]
    masters: list[tuple[dict, Path, bytes]] = []
    for icon in manifest["icons"]:
        master = REPO_ROOT / icon["master"]
        generated_bytes = None
        if "generated_from" in icon:
            generated_bytes = generate_from_sources(icon, png_rules)
            if sync and (not master.exists() or master.read_bytes() != generated_bytes):
                master.parent.mkdir(parents=True, exist_ok=True)
                master.write_bytes(generated_bytes)
                print(f"generated {icon['master']}")
        master_bytes = validate_png(master, icon, png_rules)
        if generated_bytes is not None and master_bytes != generated_bytes:
            raise ValidationError(f"{master}: bytes differ from generated source result")
        masters.append((icon, master, master_bytes))

    if sync:
        for icon, _, master_bytes in masters:
            for relative_target in icon["targets"]:
                target = REPO_ROOT / relative_target
                target.parent.mkdir(parents=True, exist_ok=True)
                if not target.exists() or target.read_bytes() != master_bytes:
                    target.write_bytes(master_bytes)
                    print(f"synced {relative_target}")

    for icon, _, master_bytes in masters:
        for relative_target in icon["targets"]:
            target = REPO_ROOT / relative_target
            target_bytes = validate_png(target, icon, png_rules)
            if target_bytes != master_bytes:
                raise ValidationError(f"{target}: bytes differ from canonical master")
        print(
            f"PASS {icon['id']}: {icon['width']}x{icon['height']} "
            f"SHA-256 {icon['sha256'].upper()}"
        )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--sync", action="store_true", help="copy masters to runtime paths")
    mode.add_argument("--check", action="store_true", help="validate without writing (default)")
    args = parser.parse_args()
    try:
        run(sync=args.sync)
    except (ValidationError, KeyError, TypeError, ValueError, zlib.error) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
