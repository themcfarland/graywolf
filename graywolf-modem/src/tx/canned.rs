//! Canned POC-C TX test frame: builds and modulates one fixed AX.25 UI
//! frame for `NW5W-8>APGRWO:!4028.56N/11150.71W< POC-C TX test - NW5W bench`
//! into a 22050 Hz mono PCM16 buffer.
//!
//! Used by the Android JNI surface (`Java_..._modemBuildTestFrame`) to prove
//! the AudioTrack TX path end-to-end. No production caller — phase 5 builds
//! frames from operator state via `pkg/ax25` + `tx::build_samples` directly.

use crate::tx::build_samples;

/// 22050 Hz mono PCM16, 1200 baud Bell 202 (1200/2200), 300 ms txdelay,
/// 100 ms txtail. Buffer length is well-defined by the modulator; for
/// `bench` it lands around 3.3 seconds of audio.
pub const SAMPLE_RATE_HZ: u32 = 22_050;
pub const BAUD: u32 = 1200;
pub const MARK_HZ: u32 = 1200;
pub const SPACE_HZ: u32 = 2200;
pub const TXDELAY_MS: u32 = 300;
pub const TXTAIL_MS: u32 = 100;

/// Hand-encoded AX.25 v2.0 UI frame bytes (no FCS — `tx::build_samples`
/// adds the FCS and the HDLC framing). Layout:
///   [0..7]  destination address  APGRWO-0
///   [7..14] source address       NW5W-8 (last-address bit set)
///   [14]    control byte         0x03 (UI)
///   [15]    PID                  0xF0 (no L3)
///   [16..]  info field           "!4028.56N/11150.71W< POC-C TX test - NW5W bench"
///
/// Address byte layout (per AX.25 v2.0 §3.12, also pkg/ax25/address.go and
/// pkg/ax25/frame.go::EncodeAddressBlock which is the project reference):
///   bytes 0..5: callsign chars left-shifted by 1, space-padded right
///   byte 6:    bit7=C-bit (dest=1, source=0 for AX.25 command frames),
///              bits6..5=RR=11, bits4..1=SSID, bit0=end-of-address
///              (0 unless last addr in the address block)
///
/// C-bit polarity matches `pkg/ax25/frame.go:134-137` default for command
/// frames (`isCommand=true`): destination C=1, source C=0.
fn canned_ax25_frame_bytes() -> Vec<u8> {
    let mut f = Vec::with_capacity(64);

    // Destination "APGRWO-0", C-bit=1 (command), end-of-address=0
    f.extend_from_slice(&[
        b'A' << 1, b'P' << 1, b'G' << 1, b'R' << 1, b'W' << 1, b'O' << 1,
        0xE0, // C=1, RR=11, SSID=0, last=0
    ]);

    // Source "NW5W-8", C-bit=0 (command), last address (no digi path),
    // end-of-address=1
    f.extend_from_slice(&[
        b'N' << 1, b'W' << 1, b'5' << 1, b'W' << 1, b' ' << 1, b' ' << 1,
        0x60 | (8 << 1) | 0x01, // 0x71: C=0, RR=11, SSID=8, last=1
    ]);

    // Control + PID
    f.push(0x03); // UI
    f.push(0xF0); // PID = no L3

    // Info field
    f.extend_from_slice(b"!4028.56N/11150.71W< POC-C TX test - NW5W bench");

    f
}

/// Build the canned POC-C TX test frame as 22050 Hz mono PCM16. Calls
/// the existing `tx::build_samples` modulator — no new DSP code.
pub fn build_canned_test_frame_pcm() -> Vec<i16> {
    let frame = canned_ax25_frame_bytes();
    build_samples(
        &frame,
        TXDELAY_MS,
        TXTAIL_MS,
        SAMPLE_RATE_HZ,
        BAUD,
        MARK_HZ,
        SPACE_HZ,
    )
    .expect("canned TX frame build_samples failed; sample rate or baud must be non-zero")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::demod_afsk::AfskDemodulator;
    use crate::rxonly::format_ax25_ui_frame;
    use crate::types::AfskProfile;

    const EXPECTED_STR: &str =
        "NW5W-8>APGRWO:!4028.56N/11150.71W< POC-C TX test - NW5W bench";

    #[test]
    fn canned_frame_round_trips_through_demodulator_at_22050_hz() {
        let samples = build_canned_test_frame_pcm();
        assert!(!samples.is_empty(), "canned frame produced zero samples");

        let mut demod = AfskDemodulator::new(
            SAMPLE_RATE_HZ,
            BAUD,
            MARK_HZ,
            SPACE_HZ,
            AfskProfile::A,
            0,
            0,
        );
        for &s in &samples {
            demod.process_sample(s as i32);
        }
        let frames = demod.take_frames();
        let decoded: Vec<String> = frames
            .iter()
            .filter_map(|f| format_ax25_ui_frame(&f.data))
            .collect();

        assert!(
            decoded.iter().any(|s| s == EXPECTED_STR),
            "canned TX frame did not round-trip; decoded={:?}",
            decoded
        );
    }

    #[test]
    fn canned_frame_buffer_length_is_in_expected_envelope() {
        // 300 ms preamble + ~536 frame bits at 1200 baud (≈ 447 ms) + 100 ms
        // tail ≈ 0.85 s of audio at 22050 Hz. Bound to 0.7..1.0 s to allow
        // for HDLC bit-stuffing overhead variance.
        let n = build_canned_test_frame_pcm().len();
        let lower = (SAMPLE_RATE_HZ as usize) * 7 / 10;  // 0.7 s
        let upper = SAMPLE_RATE_HZ as usize;             // 1.0 s
        assert!(
            (lower..=upper).contains(&n),
            "canned frame length {} samples is outside [{},{}] (≈ 0.7..1.0 s)",
            n,
            lower,
            upper
        );
    }
}
