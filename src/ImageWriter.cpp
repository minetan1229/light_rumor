#include "light_rumor/ImageWriter.h"
#include <fstream>
#include <iostream>
#include <vector>
#include <cstring>
#include <cmath>
#include <algorithm>

#if defined(LIGHT_RUMOR_ENABLE_WEBP)
#include <webp/encode.h>
#endif

namespace lightrumor {

namespace {

// Helper to write little-endian values
inline void writeU16LE(std::vector<uint8_t>& buf, uint16_t val) {
    buf.push_back(static_cast<uint8_t>(val & 0xFF));
    buf.push_back(static_cast<uint8_t>((val >> 8) & 0xFF));
}

inline void writeU32LE(std::vector<uint8_t>& buf, uint32_t val) {
    buf.push_back(static_cast<uint8_t>(val & 0xFF));
    buf.push_back(static_cast<uint8_t>((val >> 8) & 0xFF));
    buf.push_back(static_cast<uint8_t>((val >> 16) & 0xFF));
    buf.push_back(static_cast<uint8_t>((val >> 24) & 0xFF));
}

// Helper to write big-endian values (for JPEG markers)
inline void writeU16BE(std::vector<uint8_t>& buf, uint16_t val) {
    buf.push_back(static_cast<uint8_t>((val >> 8) & 0xFF));
    buf.push_back(static_cast<uint8_t>(val & 0xFF));
}

// IFD Tag helper
struct IFDEntry {
    uint16_t tag;
    uint16_t type;  // 1: BYTE, 2: ASCII, 3: SHORT, 4: LONG, 5: RATIONAL, 7: UNDEFINED, 10: SRATIONAL
    uint32_t count;
    uint32_t valueOrOffset;
    std::vector<uint8_t> data; // if size > 4
};

} // namespace

std::vector<uint8_t> ImageWriter::buildExifPayload(const ExifMetadata& metadata) {
    std::vector<uint8_t> payload;

    // Header: "Exif\0\0"
    const char exifHeader[] = "Exif\0\0";
    payload.insert(payload.end(), exifHeader, exifHeader + 6);

    // TIFF Header: Little Endian 'II' + 42 (0x002A) + offset to IFD0 (8 bytes from TIFF header start)
    (void)payload.size(); // tiffStart
    writeU16LE(payload, 0x4949); // 'II'
    writeU16LE(payload, 0x002A); // 42
    writeU32LE(payload, 8);      // IFD0 offset relative to tiffStart

    // We will build IFD0, Exif SubIFD, and GPS IFD
    std::vector<IFDEntry> ifd0Entries;
    std::vector<IFDEntry> exifEntries;
    std::vector<IFDEntry> gpsEntries;

    auto addString = [](std::vector<IFDEntry>& list, uint16_t tag, const std::string& str) {
        IFDEntry entry;
        entry.tag = tag;
        entry.type = 2; // ASCII
        entry.count = static_cast<uint32_t>(str.length() + 1);
        if (entry.count <= 4) {
            entry.valueOrOffset = 0;
            std::memcpy(&entry.valueOrOffset, str.c_str(), str.length() + 1);
        } else {
            entry.data.assign(str.c_str(), str.c_str() + str.length() + 1);
            entry.valueOrOffset = 0; // filled later
        }
        list.push_back(entry);
    };

    auto addShort = [](std::vector<IFDEntry>& list, uint16_t tag, uint16_t val) {
        IFDEntry entry;
        entry.tag = tag;
        entry.type = 3; // SHORT
        entry.count = 1;
        entry.valueOrOffset = val;
        list.push_back(entry);
    };

    auto addLong = [](std::vector<IFDEntry>& list, uint16_t tag, uint32_t val) {
        IFDEntry entry;
        entry.tag = tag;
        entry.type = 4; // LONG
        entry.count = 1;
        entry.valueOrOffset = val;
        list.push_back(entry);
    };

    auto addRational = [](std::vector<IFDEntry>& list, uint16_t tag, uint32_t num, uint32_t den) {
        IFDEntry entry;
        entry.tag = tag;
        entry.type = 5; // RATIONAL
        entry.count = 1;
        entry.valueOrOffset = 0; // filled later
        entry.data.resize(8);
        entry.data[0] = static_cast<uint8_t>(num & 0xFF);
        entry.data[1] = static_cast<uint8_t>((num >> 8) & 0xFF);
        entry.data[2] = static_cast<uint8_t>((num >> 16) & 0xFF);
        entry.data[3] = static_cast<uint8_t>((num >> 24) & 0xFF);
        entry.data[4] = static_cast<uint8_t>(den & 0xFF);
        entry.data[5] = static_cast<uint8_t>((den >> 8) & 0xFF);
        entry.data[6] = static_cast<uint8_t>((den >> 16) & 0xFF);
        entry.data[7] = static_cast<uint8_t>((den >> 24) & 0xFF);
        list.push_back(entry);
    };

    // Populate IFD0
    addString(ifd0Entries, 0x010F, metadata.make);             // Make
    addString(ifd0Entries, 0x0110, metadata.model);            // Model
    addString(ifd0Entries, 0x0131, metadata.software);         // Software
    addString(ifd0Entries, 0x0132, metadata.dateTimeOriginal); // DateTime
    addLong(ifd0Entries, 0x8769, 0); // Exif IFD Pointer (to be resolved)
    if (metadata.hasGps) {
        addLong(ifd0Entries, 0x8825, 0); // GPS IFD Pointer (to be resolved)
    }

    // Populate Exif SubIFD
    // Exposure Time
    uint32_t expNum = 1;
    uint32_t expDen = metadata.exposureTime > 0 ? static_cast<uint32_t>(std::round(1.0 / metadata.exposureTime)) : 250;
    if (expDen == 0) expDen = 1;
    addRational(exifEntries, 0x829A, expNum, expDen); // ExposureTime

    // F-Number
    uint32_t fNum = static_cast<uint32_t>(std::round(metadata.fNumber * 10.0));
    addRational(exifEntries, 0x829D, fNum, 10); // FNumber

    addShort(exifEntries, 0x8827, static_cast<uint16_t>(metadata.isoSpeed)); // ISO
    addString(exifEntries, 0x9003, metadata.dateTimeOriginal); // DateTimeOriginal

    // Focal Length
    uint32_t focalNum = static_cast<uint32_t>(std::round(metadata.focalLength * 10.0));
    addRational(exifEntries, 0x920A, focalNum, 10); // FocalLength
    addString(exifEntries, 0xA434, metadata.lensModel); // LensModel

    // Populate GPS IFD
    if (metadata.hasGps) {
        // Latitude Ref "N" or "S"
        std::string latRef = metadata.gpsLatitude >= 0 ? "N" : "S";
        addString(gpsEntries, 0x0001, latRef);
        // Latitude Degrees, Minutes, Seconds
        double lat = std::abs(metadata.gpsLatitude);
        uint32_t latDeg = static_cast<uint32_t>(lat);
        double latMinFrac = (lat - latDeg) * 60.0;
        uint32_t latMin = static_cast<uint32_t>(latMinFrac);
        uint32_t latSec = static_cast<uint32_t>(std::round((latMinFrac - latMin) * 6000.0));
        {
            IFDEntry entry;
            entry.tag = 0x0002;
            entry.type = 5;
            entry.count = 3;
            entry.valueOrOffset = 0;
            entry.data.resize(24);
            auto putR = [&](int idx, uint32_t n, uint32_t d) {
                entry.data[idx * 8 + 0] = static_cast<uint8_t>(n & 0xFF);
                entry.data[idx * 8 + 1] = static_cast<uint8_t>((n >> 8) & 0xFF);
                entry.data[idx * 8 + 2] = static_cast<uint8_t>((n >> 16) & 0xFF);
                entry.data[idx * 8 + 3] = static_cast<uint8_t>((n >> 24) & 0xFF);
                entry.data[idx * 8 + 4] = static_cast<uint8_t>(d & 0xFF);
                entry.data[idx * 8 + 5] = static_cast<uint8_t>((d >> 8) & 0xFF);
                entry.data[idx * 8 + 6] = static_cast<uint8_t>((d >> 16) & 0xFF);
                entry.data[idx * 8 + 7] = static_cast<uint8_t>((d >> 24) & 0xFF);
            };
            putR(0, latDeg, 1);
            putR(1, latMin, 1);
            putR(2, latSec, 100);
            gpsEntries.push_back(entry);
        }

        // Longitude Ref "E" or "W"
        std::string lonRef = metadata.gpsLongitude >= 0 ? "E" : "W";
        addString(gpsEntries, 0x0003, lonRef);
        double lon = std::abs(metadata.gpsLongitude);
        uint32_t lonDeg = static_cast<uint32_t>(lon);
        double lonMinFrac = (lon - lonDeg) * 60.0;
        uint32_t lonMin = static_cast<uint32_t>(lonMinFrac);
        uint32_t lonSec = static_cast<uint32_t>(std::round((lonMinFrac - lonMin) * 6000.0));
        {
            IFDEntry entry;
            entry.tag = 0x0004;
            entry.type = 5;
            entry.count = 3;
            entry.valueOrOffset = 0;
            entry.data.resize(24);
            auto putR = [&](int idx, uint32_t n, uint32_t d) {
                entry.data[idx * 8 + 0] = static_cast<uint8_t>(n & 0xFF);
                entry.data[idx * 8 + 1] = static_cast<uint8_t>((n >> 8) & 0xFF);
                entry.data[idx * 8 + 2] = static_cast<uint8_t>((n >> 16) & 0xFF);
                entry.data[idx * 8 + 3] = static_cast<uint8_t>((n >> 24) & 0xFF);
                entry.data[idx * 8 + 4] = static_cast<uint8_t>(d & 0xFF);
                entry.data[idx * 8 + 5] = static_cast<uint8_t>((d >> 8) & 0xFF);
                entry.data[idx * 8 + 6] = static_cast<uint8_t>((d >> 16) & 0xFF);
                entry.data[idx * 8 + 7] = static_cast<uint8_t>((d >> 24) & 0xFF);
            };
            putR(0, lonDeg, 1);
            putR(1, lonMin, 1);
            putR(2, lonSec, 100);
            gpsEntries.push_back(entry);
        }

        // Altitude
        uint8_t altRef = metadata.gpsAltitude >= 0 ? 0 : 1;
        {
            IFDEntry entry;
            entry.tag = 0x0005;
            entry.type = 1;
            entry.count = 1;
            entry.valueOrOffset = altRef;
            gpsEntries.push_back(entry);
        }
        uint32_t altNum = static_cast<uint32_t>(std::abs(metadata.gpsAltitude) * 100.0);
        addRational(gpsEntries, 0x0006, altNum, 100);
    }

    // Sort entries by tag (required by TIFF specification)
    auto sortEntries = [](std::vector<IFDEntry>& list) {
        std::sort(list.begin(), list.end(), [](const IFDEntry& a, const IFDEntry& b) {
            return a.tag < b.tag;
        });
    };
    sortEntries(ifd0Entries);
    sortEntries(exifEntries);
    sortEntries(gpsEntries);

    // Calculate offsets
    // IFD0 begins at tiffStart + 8
    uint32_t ifd0Offset = 8;
    uint32_t ifd0Size = 2 + static_cast<uint32_t>(ifd0Entries.size()) * 12 + 4;

    uint32_t exifOffset = ifd0Offset + ifd0Size;
    uint32_t exifSize = 2 + static_cast<uint32_t>(exifEntries.size()) * 12 + 4;

    uint32_t gpsOffset = exifOffset + exifSize;
    uint32_t gpsSize = metadata.hasGps ? (2 + static_cast<uint32_t>(gpsEntries.size()) * 12 + 4) : 0;

    uint32_t dataOffset = gpsOffset + gpsSize;

    // Update pointers in IFD0
    for (auto& entry : ifd0Entries) {
        if (entry.tag == 0x8769) {
            entry.valueOrOffset = exifOffset;
        } else if (entry.tag == 0x8825) {
            entry.valueOrOffset = gpsOffset;
        }
    }

    // Allocate buffer for all IFDs and data
    std::vector<uint8_t> tiffBody;
    std::vector<uint8_t> dataHeap;

    auto appendIFD = [&](const std::vector<IFDEntry>& list, uint32_t nextIFDOffset) {
        writeU16LE(tiffBody, static_cast<uint16_t>(list.size()));
        for (const auto& entry : list) {
            writeU16LE(tiffBody, entry.tag);
            writeU16LE(tiffBody, entry.type);
            writeU32LE(tiffBody, entry.count);
            if (entry.data.empty()) {
                writeU32LE(tiffBody, entry.valueOrOffset);
            } else {
                uint32_t offset = dataOffset + static_cast<uint32_t>(dataHeap.size());
                writeU32LE(tiffBody, offset);
                dataHeap.insert(dataHeap.end(), entry.data.begin(), entry.data.end());
                if (entry.data.size() % 2 != 0) dataHeap.push_back(0); // Word alignment
            }
        }
        writeU32LE(tiffBody, nextIFDOffset);
    };

    appendIFD(ifd0Entries, 0);
    appendIFD(exifEntries, 0);
    if (metadata.hasGps) {
        appendIFD(gpsEntries, 0);
    }

    payload.insert(payload.end(), tiffBody.begin(), tiffBody.end());
    payload.insert(payload.end(), dataHeap.begin(), dataHeap.end());

    return payload;
}

// -------------------------------------------------------------------------
// Fast Baseline JPEG Encoder (Supporting 4:4:4 and 4:2:0 + Exif APP1)
// -------------------------------------------------------------------------
namespace {

// Standard JPEG luminance and chrominance quantization tables
static const uint8_t std_luminance_quant[64] = {
    16, 11, 10, 16,  24,  40,  51,  61,
    12, 12, 14, 19,  26,  58,  60,  55,
    14, 13, 16, 24,  40,  57,  69,  56,
    14, 17, 22, 29,  51,  87,  80,  62,
    18, 22, 37, 56,  68, 109, 103,  77,
    24, 35, 55, 64,  81, 104, 113,  92,
    49, 64, 78, 87, 103, 121, 120, 101,
    72, 92, 95, 98, 112, 100, 103,  99
};

static const uint8_t std_chrominance_quant[64] = {
    17, 18, 24, 47, 99, 99, 99, 99,
    18, 21, 26, 66, 99, 99, 99, 99,
    24, 26, 56, 99, 99, 99, 99, 99,
    47, 66, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99
};

// Zig-zag order
static const uint8_t zigzag[64] = {
     0,  1,  8, 16,  9,  2,  3, 10,
    17, 24, 32, 25, 18, 11,  4,  5,
    12, 19, 26, 33, 40, 48, 41, 34,
    27, 20, 13,  6,  7, 14, 21, 28,
    35, 42, 49, 56, 57, 50, 43, 36,
    29, 22, 15, 23, 30, 37, 44, 51,
    58, 59, 52, 45, 38, 31, 39, 46,
    53, 60, 61, 54, 47, 55, 62, 63
};

// Forward DCT on 8x8 block
void forwardDCT(const float inBlock[64], float outBlock[64]) {
    static const double PI = 3.14159265358979323846;
    for (int v = 0; v < 8; ++v) {
        for (int u = 0; u < 8; ++u) {
            double sum = 0.0;
            double cu = (u == 0) ? 1.0 / std::sqrt(2.0) : 1.0;
            double cv = (v == 0) ? 1.0 / std::sqrt(2.0) : 1.0;

            for (int y = 0; y < 8; ++y) {
                for (int x = 0; x < 8; ++x) {
                    sum += inBlock[y * 8 + x] *
                           std::cos((2 * x + 1) * u * PI / 16.0) *
                           std::cos((2 * y + 1) * v * PI / 16.0);
                }
            }
            outBlock[v * 8 + u] = static_cast<float>(0.25 * cu * cv * sum);
        }
    }
}

// Bit writer for Huffman stream
class JpegBitWriter {
public:
    std::vector<uint8_t>& stream;
    uint64_t bitBuffer = 0;
    int bitCount = 0;

    JpegBitWriter(std::vector<uint8_t>& s) : stream(s) {}

    void writeBits(uint32_t code, int numBits) {
        bitBuffer = (bitBuffer << numBits) | (static_cast<uint64_t>(code) & ((1ULL << numBits) - 1ULL));
        bitCount += numBits;
        while (bitCount >= 8) {
            uint8_t byte = static_cast<uint8_t>((bitBuffer >> (bitCount - 8)) & 0xFF);
            stream.push_back(byte);
            if (byte == 0xFF) {
                stream.push_back(0x00); // Byte stuffing
            }
            bitCount -= 8;
        }
    }

    void flush() {
        if (bitCount > 0) {
            uint8_t byte = static_cast<uint8_t>((bitBuffer << (8 - bitCount)) & 0xFF);
            stream.push_back(byte);
            if (byte == 0xFF) {
                stream.push_back(0x00);
            }
            bitBuffer = 0;
            bitCount = 0;
        }
    }
};

// Standard Huffman tables
static const uint8_t dc_lum_bits[16] = {0,1,5,1,1,1,1,1,1,0,0,0,0,0,0,0};
static const uint8_t dc_lum_val[] = {0,1,2,3,4,5,6,7,8,9,10,11};

static const uint8_t dc_chrom_bits[16] = {0,3,1,1,1,1,1,1,1,1,1,0,0,0,0,0};
static const uint8_t dc_chrom_val[] = {0,1,2,3,4,5,6,7,8,9,10,11};

static const uint8_t ac_lum_bits[16] = {0,2,1,3,3,2,4,3,5,5,4,4,0,0,1,0x7d};
static const uint8_t ac_lum_val[] = {
    0x01,0x02,0x03,0x00,0x04,0x11,0x05,0x12,0x21,0x31,0x41,0x06,0x13,0x51,0x61,0x07,
    0x22,0x71,0x14,0x32,0x81,0x91,0xa1,0x08,0x23,0x42,0xb1,0xc1,0x15,0x52,0xd1,0xf0,
    0x24,0x33,0x62,0x72,0x82,0x09,0x0a,0x16,0x17,0x18,0x19,0x1a,0x25,0x26,0x27,0x28,
    0x29,0x2a,0x34,0x35,0x36,0x37,0x38,0x39,0x3a,0x43,0x44,0x45,0x46,0x47,0x48,0x49,
    0x4a,0x53,0x54,0x55,0x56,0x57,0x58,0x59,0x5a,0x63,0x64,0x65,0x66,0x67,0x68,0x69,
    0x6a,0x73,0x74,0x75,0x76,0x77,0x78,0x79,0x7a,0x83,0x84,0x85,0x86,0x87,0x88,0x89,
    0x8a,0x92,0x93,0x94,0x95,0x96,0x97,0x98,0x99,0x9a,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,
    0xa8,0xa9,0xaa,0xb2,0xb3,0xb4,0xb5,0xb6,0xb7,0xb8,0xb9,0xba,0xc2,0xc3,0xc4,0xc5,
    0xc6,0xc7,0xc8,0xc9,0xca,0xd2,0xd3,0xd4,0xd5,0xd6,0xd7,0xd8,0xd9,0xda,0xe1,0xe2,
    0xe3,0xe4,0xe5,0xe6,0xe7,0xe8,0xe9,0xea,0xf1,0xf2,0xf3,0xf4,0xf5,0xf6,0xf7,0xf8,
    0xf9,0xfa
};

static const uint8_t ac_chrom_bits[16] = {0,2,1,2,4,4,3,4,7,5,4,4,0,1,2,0x77};
static const uint8_t ac_chrom_val[] = {
    0x00,0x01,0x02,0x03,0x11,0x04,0x05,0x21,0x31,0x06,0x12,0x41,0x51,0x07,0x61,0x71,
    0x13,0x22,0x32,0x81,0x08,0x14,0x42,0x91,0xa1,0xb1,0xc1,0x09,0x23,0x33,0x52,0xf0,
    0x15,0x62,0x72,0xd1,0x0a,0x16,0x24,0x34,0xe1,0x25,0xf1,0x17,0x18,0x19,0x1a,0x26,
    0x27,0x28,0x29,0x2a,0x35,0x36,0x37,0x38,0x39,0x3a,0x43,0x44,0x45,0x46,0x47,0x48,
    0x49,0x4a,0x53,0x54,0x55,0x56,0x57,0x58,0x59,0x5a,0x63,0x64,0x65,0x66,0x67,0x68,
    0x69,0x6a,0x73,0x74,0x75,0x76,0x77,0x78,0x79,0x7a,0x82,0x83,0x84,0x85,0x86,0x87,
    0x88,0x89,0x8a,0x92,0x93,0x94,0x95,0x96,0x97,0x98,0x99,0x9a,0xa2,0xa3,0xa4,0xa5,
    0xa6,0xa7,0xa8,0xa9,0xaa,0xb2,0xb3,0xb4,0xb5,0xb6,0xb7,0xb8,0xb9,0xba,0xc2,0xc3,
    0xc4,0xc5,0xc6,0xc7,0xc8,0xc9,0xca,0xd2,0xd3,0xd4,0xd5,0xd6,0xd7,0xd8,0xd9,0xda,
    0xe2,0xe3,0xe4,0xe5,0xe6,0xe7,0xe8,0xe9,0xea,0xf2,0xf3,0xf4,0xf5,0xf6,0xf7,0xf8,
    0xf9,0xfa
};

struct HuffmanCode {
    uint16_t code;
    uint8_t size;
};

void buildHuffmanTable(const uint8_t* bits, const uint8_t* val, HuffmanCode* table, int maxVal) {
    for (int i = 0; i <= maxVal; ++i) table[i].size = 0;
    uint16_t code = 0;
    int k = 0;
    for (int i = 1; i <= 16; ++i) {
        for (int j = 0; j < bits[i - 1]; ++j) {
            uint8_t v = val[k++];
            if (v <= maxVal) {
                table[v].code = code;
                table[v].size = static_cast<uint8_t>(i);
            }
            code++;
        }
        code <<= 1;
    }
}

void computeHuffmanValue(int val, uint32_t& code, int& numBits) {
    if (val == 0) {
        code = 0;
        numBits = 0;
        return;
    }
    int absVal = std::abs(val);
    numBits = 0;
    while (absVal > 0) {
        numBits++;
        absVal >>= 1;
    }
    code = (val > 0) ? static_cast<uint32_t>(val) : static_cast<uint32_t>(val + (1 << numBits) - 1);
}

void encodeBlock(JpegBitWriter& writer,
                 const int16_t block[64],
                 int16_t& prevDC,
                 const HuffmanCode* dcTable,
                 const HuffmanCode* acTable) {
    // DC encoding
    int16_t diff = block[0] - prevDC;
    prevDC = block[0];

    uint32_t valCode;
    int valBits;
    computeHuffmanValue(diff, valCode, valBits);
    writer.writeBits(dcTable[valBits].code, dcTable[valBits].size);
    if (valBits > 0) {
        writer.writeBits(valCode, valBits);
    }

    // AC encoding (zig-zag 1..63)
    int r = 0;
    for (int k = 1; k < 64; ++k) {
        int16_t ac = block[zigzag[k]];
        if (ac == 0) {
            r++;
        } else {
            while (r > 15) {
                writer.writeBits(acTable[0xF0].code, acTable[0xF0].size); // ZRL
                r -= 16;
            }
            computeHuffmanValue(ac, valCode, valBits);
            uint8_t symbol = static_cast<uint8_t>((r << 4) | valBits);
            writer.writeBits(acTable[symbol].code, acTable[symbol].size);
            writer.writeBits(valCode, valBits);
            r = 0;
        }
    }
    if (r > 0) {
        writer.writeBits(acTable[0x00].code, acTable[0x00].size); // EOB
    }
}

} // namespace

bool ImageWriter::writeJPEGMemory(const uint8_t* rgbData,
                                  int32_t width, int32_t height,
                                  int32_t quality,
                                  ChromaSubsampling subsampling,
                                  const ExifMetadata* metadata,
                                  std::vector<uint8_t>& outJpegBytes) {
    if (!rgbData || width <= 0 || height <= 0) return false;

    quality = std::clamp(quality, 1, 100);
    int scale = (quality < 50) ? (5000 / quality) : (200 - quality * 2);

    // Natural-order quantization tables (Annex K) scaled by quality factor
    uint8_t qLumNatural[64], qChromNatural[64];
    uint8_t qLumZigzag[64], qChromZigzag[64];
    for (int i = 0; i < 64; ++i) {
        int lVal = (static_cast<int>(std_luminance_quant[i]) * scale + 50) / 100;
        int cVal = (static_cast<int>(std_chrominance_quant[i]) * scale + 50) / 100;
        qLumNatural[i] = static_cast<uint8_t>(std::clamp(lVal, 1, 255));
        qChromNatural[i] = static_cast<uint8_t>(std::clamp(cVal, 1, 255));
    }

    // Build Zigzag-ordered quantization tables for DQT segment (ITU-T T.81 B.2.4.1)
    for (int i = 0; i < 64; ++i) {
        qLumZigzag[i] = qLumNatural[zigzag[i]];
        qChromZigzag[i] = qChromNatural[zigzag[i]];
    }

    std::vector<uint8_t>& jpeg = outJpegBytes;
    jpeg.clear();
    jpeg.reserve(static_cast<size_t>(width) * height / 4);

    // SOI (Start of Image)
    jpeg.push_back(0xFF);
    jpeg.push_back(0xD8);

    // Embed Exif APP1 if requested
    if (metadata) {
        std::vector<uint8_t> exif = buildExifPayload(*metadata);
        if (exif.size() + 2 <= 0xFFFF) {
            jpeg.push_back(0xFF);
            jpeg.push_back(0xE1); // APP1
            writeU16BE(jpeg, static_cast<uint16_t>(exif.size() + 2));
            jpeg.insert(jpeg.end(), exif.begin(), exif.end());
        }
    }

    // DQT (Define Quantization Table)
    jpeg.push_back(0xFF);
    jpeg.push_back(0xDB);
    writeU16BE(jpeg, 2 + 65 * 2);
    jpeg.push_back(0x00); // Table 0 (Lum)
    for (int i = 0; i < 64; ++i) jpeg.push_back(qLumZigzag[i]);
    jpeg.push_back(0x01); // Table 1 (Chrom)
    for (int i = 0; i < 64; ++i) jpeg.push_back(qChromZigzag[i]);

    // SOF0 (Baseline DCT)
    jpeg.push_back(0xFF);
    jpeg.push_back(0xC0);
    writeU16BE(jpeg, 17);
    jpeg.push_back(8); // 8-bit precision
    writeU16BE(jpeg, static_cast<uint16_t>(height));
    writeU16BE(jpeg, static_cast<uint16_t>(width));
    jpeg.push_back(3); // 3 components (Y, Cb, Cr)

    bool is444 = (subsampling == ChromaSubsampling::YUV444);
    uint8_t hY = is444 ? 1 : 2;
    uint8_t vY = is444 ? 1 : 2;

    jpeg.push_back(1); // Y
    jpeg.push_back(static_cast<uint8_t>((hY << 4) | vY));
    jpeg.push_back(0); // DQT 0

    jpeg.push_back(2); // Cb
    jpeg.push_back(0x11);
    jpeg.push_back(1); // DQT 1

    jpeg.push_back(3); // Cr
    jpeg.push_back(0x11);
    jpeg.push_back(1); // DQT 1

    // DHT (Define Huffman Tables)
    auto writeDHT = [&](uint8_t index, const uint8_t* bits, const uint8_t* val, size_t valLen) {
        jpeg.push_back(0xFF);
        jpeg.push_back(0xC4);
        writeU16BE(jpeg, static_cast<uint16_t>(2 + 1 + 16 + valLen));
        jpeg.push_back(index);
        for (int i = 0; i < 16; ++i) jpeg.push_back(bits[i]);
        jpeg.insert(jpeg.end(), val, val + valLen);
    };

    writeDHT(0x00, dc_lum_bits, dc_lum_val, sizeof(dc_lum_val));
    writeDHT(0x10, ac_lum_bits, ac_lum_val, sizeof(ac_lum_val));
    writeDHT(0x01, dc_chrom_bits, dc_chrom_val, sizeof(dc_chrom_val));
    writeDHT(0x11, ac_chrom_bits, ac_chrom_val, sizeof(ac_chrom_val));

    // SOS (Start of Scan)
    jpeg.push_back(0xFF);
    jpeg.push_back(0xDA);
    writeU16BE(jpeg, 12);
    jpeg.push_back(3);
    jpeg.push_back(1); jpeg.push_back(0x00);
    jpeg.push_back(2); jpeg.push_back(0x11);
    jpeg.push_back(3); jpeg.push_back(0x11);
    jpeg.push_back(0);  // Spectral start
    jpeg.push_back(63); // Spectral end
    jpeg.push_back(0);  // Successive approx

    // Prepare Huffman lookup tables
    HuffmanCode dcLumT[16], acLumT[256], dcChromT[16], acChromT[256];
    buildHuffmanTable(dc_lum_bits, dc_lum_val, dcLumT, 15);
    buildHuffmanTable(ac_lum_bits, ac_lum_val, acLumT, 255);
    buildHuffmanTable(dc_chrom_bits, dc_chrom_val, dcChromT, 15);
    buildHuffmanTable(ac_chrom_bits, ac_chrom_val, acChromT, 255);

    JpegBitWriter writer(jpeg);
    int16_t prevDC_Y = 0, prevDC_Cb = 0, prevDC_Cr = 0;

    int mcuW = is444 ? 8 : 16;
    int mcuH = is444 ? 8 : 16;

    for (int mcuY = 0; mcuY < height; mcuY += mcuH) {
        for (int mcuX = 0; mcuX < width; mcuX += mcuW) {
            if (is444) {
                // 1 Y, 1 Cb, 1 Cr block (8x8)
                float blockY[64], blockCb[64], blockCr[64];
                for (int y = 0; y < 8; ++y) {
                    int py = std::min(mcuY + y, height - 1);
                    for (int x = 0; x < 8; ++x) {
                        int px = std::min(mcuX + x, width - 1);
                        size_t idx = (static_cast<size_t>(py) * width + px) * 3;
                        float r = rgbData[idx + 0];
                        float g = rgbData[idx + 1];
                        float b = rgbData[idx + 2];

                        float Y  =  0.29900f * r + 0.58700f * g + 0.11400f * b;
                        float Cb = -0.16874f * r - 0.33126f * g + 0.50000f * b + 128.0f;
                        float Cr =  0.50000f * r - 0.41869f * g - 0.08131f * b + 128.0f;

                        blockY[y * 8 + x]  = Y - 128.0f;
                        blockCb[y * 8 + x] = Cb - 128.0f;
                        blockCr[y * 8 + x] = Cr - 128.0f;
                    }
                }

                auto quantizeAndEncode = [&](const float inBlock[64], const uint8_t qTableNatural[64], int16_t& prevDC, const HuffmanCode* dcT, const HuffmanCode* acT) {
                    float dct[64];
                    int16_t qDct[64];
                    forwardDCT(inBlock, dct);
                    for (int i = 0; i < 64; ++i) {
                        qDct[i] = static_cast<int16_t>(std::round(dct[i] / qTableNatural[i]));
                    }
                    encodeBlock(writer, qDct, prevDC, dcT, acT);
                };

                quantizeAndEncode(blockY, qLumNatural, prevDC_Y, dcLumT, acLumT);
                quantizeAndEncode(blockCb, qChromNatural, prevDC_Cb, dcChromT, acChromT);
                quantizeAndEncode(blockCr, qChromNatural, prevDC_Cr, dcChromT, acChromT);
            } else {
                // 4:2:0: 4 Y blocks (8x8) and 1 Cb, 1 Cr block (8x8)
                float blockY[4][64];
                float blockCb[64] = {0};
                float blockCr[64] = {0};

                for (int subY = 0; subY < 2; ++subY) {
                    for (int subX = 0; subX < 2; ++subX) {
                        int bIdx = subY * 2 + subX;
                        for (int y = 0; y < 8; ++y) {
                            int py = std::min(mcuY + subY * 8 + y, height - 1);
                            for (int x = 0; x < 8; ++x) {
                                int px = std::min(mcuX + subX * 8 + x, width - 1);
                                size_t idx = (static_cast<size_t>(py) * width + px) * 3;
                                float r = rgbData[idx + 0];
                                float g = rgbData[idx + 1];
                                float b = rgbData[idx + 2];

                                float Y  =  0.29900f * r + 0.58700f * g + 0.11400f * b;
                                blockY[bIdx][y * 8 + x] = Y - 128.0f;
                            }
                        }
                    }
                }

                // Downsample Cb, Cr 2x2
                for (int y = 0; y < 8; ++y) {
                    for (int x = 0; x < 8; ++x) {
                        float cbSum = 0.0f, crSum = 0.0f;
                        for (int dy = 0; dy < 2; ++dy) {
                            int py = std::min(mcuY + y * 2 + dy, height - 1);
                            for (int dx = 0; dx < 2; ++dx) {
                                int px = std::min(mcuX + x * 2 + dx, width - 1);
                                size_t idx = (static_cast<size_t>(py) * width + px) * 3;
                                float r = rgbData[idx + 0];
                                float g = rgbData[idx + 1];
                                float b = rgbData[idx + 2];
                                cbSum += -0.16874f * r - 0.33126f * g + 0.50000f * b;
                                crSum +=  0.50000f * r - 0.41869f * g - 0.08131f * b;
                            }
                        }
                        blockCb[y * 8 + x] = cbSum * 0.25f;
                        blockCr[y * 8 + x] = crSum * 0.25f;
                    }
                }

                auto quantizeAndEncode = [&](const float inBlock[64], const uint8_t qTableNatural[64], int16_t& prevDC, const HuffmanCode* dcT, const HuffmanCode* acT) {
                    float dct[64];
                    int16_t qDct[64];
                    forwardDCT(inBlock, dct);
                    for (int i = 0; i < 64; ++i) {
                        qDct[i] = static_cast<int16_t>(std::round(dct[i] / qTableNatural[i]));
                    }
                    encodeBlock(writer, qDct, prevDC, dcT, acT);
                };

                for (int b = 0; b < 4; ++b) {
                    quantizeAndEncode(blockY[b], qLumNatural, prevDC_Y, dcLumT, acLumT);
                }
                quantizeAndEncode(blockCb, qChromNatural, prevDC_Cb, dcChromT, acChromT);
                quantizeAndEncode(blockCr, qChromNatural, prevDC_Cr, dcChromT, acChromT);
            }
        }
    }

    writer.flush();

    // EOI (End of Image)
    jpeg.push_back(0xFF);
    jpeg.push_back(0xD9);

    return true;
}

bool ImageWriter::writeJPEG(const std::string& filePath,
                            const uint8_t* rgbData,
                            int32_t width, int32_t height,
                            int32_t quality,
                            ChromaSubsampling subsampling,
                            const ExifMetadata* metadata) {
    std::vector<uint8_t> jpeg;
    if (!writeJPEGMemory(rgbData, width, height, quality, subsampling, metadata, jpeg)) {
        return false;
    }
    std::ofstream file(filePath, std::ios::binary);
    if (!file) return false;
    file.write(reinterpret_cast<const char*>(jpeg.data()), jpeg.size());
    return true;
}

// -------------------------------------------------------------------------
// Standard TIFF 6.0 Writer (16-bit / 8-bit RGB with Exif & Metadata)
// -------------------------------------------------------------------------
bool ImageWriter::writeTIFF16(const std::string& filePath,
                              const uint16_t* rgb16Data,
                              int32_t width, int32_t height,
                              const ExifMetadata* metadata) {
    if (!rgb16Data || width <= 0 || height <= 0) return false;

    std::vector<uint8_t> tiff;
    tiff.reserve(1024);

    // TIFF Header: Little Endian 'II' + 42 + offset to IFD0 (8)
    writeU16LE(tiff, 0x4949); // 'II'
    writeU16LE(tiff, 0x002A); // 42
    writeU32LE(tiff, 8);      // IFD0 offset

    std::vector<IFDEntry> ifd0;
    std::vector<IFDEntry> exifSubIFD;

    auto addShort = [](std::vector<IFDEntry>& list, uint16_t tag, uint16_t val) {
        IFDEntry e; e.tag = tag; e.type = 3; e.count = 1; e.valueOrOffset = val;
        list.push_back(e);
    };

    auto addLong = [](std::vector<IFDEntry>& list, uint16_t tag, uint32_t val) {
        IFDEntry e; e.tag = tag; e.type = 4; e.count = 1; e.valueOrOffset = val;
        list.push_back(e);
    };

    auto addShort3 = [](std::vector<IFDEntry>& list, uint16_t tag, uint16_t v0, uint16_t v1, uint16_t v2) {
        IFDEntry e; e.tag = tag; e.type = 3; e.count = 3; e.valueOrOffset = 0;
        e.data.resize(6);
        e.data[0] = static_cast<uint8_t>(v0 & 0xFF); e.data[1] = static_cast<uint8_t>((v0 >> 8) & 0xFF);
        e.data[2] = static_cast<uint8_t>(v1 & 0xFF); e.data[3] = static_cast<uint8_t>((v1 >> 8) & 0xFF);
        e.data[4] = static_cast<uint8_t>(v2 & 0xFF); e.data[5] = static_cast<uint8_t>((v2 >> 8) & 0xFF);
        list.push_back(e);
    };

    auto addString = [](std::vector<IFDEntry>& list, uint16_t tag, const std::string& str) {
        IFDEntry e; e.tag = tag; e.type = 2; e.count = static_cast<uint32_t>(str.length() + 1);
        if (e.count <= 4) {
            e.valueOrOffset = 0;
            std::memcpy(&e.valueOrOffset, str.c_str(), str.length() + 1);
        } else {
            e.data.assign(str.c_str(), str.c_str() + str.length() + 1);
            e.valueOrOffset = 0;
        }
        list.push_back(e);
    };

    auto addRational = [](std::vector<IFDEntry>& list, uint16_t tag, uint32_t num, uint32_t den) {
        IFDEntry e; e.tag = tag; e.type = 5; e.count = 1; e.valueOrOffset = 0;
        e.data.resize(8);
        e.data[0] = static_cast<uint8_t>(num & 0xFF); e.data[1] = static_cast<uint8_t>((num >> 8) & 0xFF);
        e.data[2] = static_cast<uint8_t>((num >> 16) & 0xFF); e.data[3] = static_cast<uint8_t>((num >> 24) & 0xFF);
        e.data[4] = static_cast<uint8_t>(den & 0xFF); e.data[5] = static_cast<uint8_t>((den >> 8) & 0xFF);
        e.data[6] = static_cast<uint8_t>((den >> 16) & 0xFF); e.data[7] = static_cast<uint8_t>((den >> 24) & 0xFF);
        list.push_back(e);
    };

    // Standard TIFF 6.0 baseline tags
    addLong(ifd0, 0x0100, static_cast<uint32_t>(width));  // ImageWidth
    addLong(ifd0, 0x0101, static_cast<uint32_t>(height)); // ImageLength
    addShort3(ifd0, 0x0102, 16, 16, 16);                 // BitsPerSample (16, 16, 16)
    addShort(ifd0, 0x0103, 1);                            // Compression = 1 (None)
    addShort(ifd0, 0x0106, 2);                            // PhotometricInterpretation = 2 (RGB)
    addLong(ifd0, 0x0111, 0);                             // StripOffsets (filled later)
    addShort(ifd0, 0x0115, 3);                            // SamplesPerPixel = 3
    addLong(ifd0, 0x0116, static_cast<uint32_t>(height)); // RowsPerStrip = full image
    uint32_t pixelByteCount = static_cast<uint32_t>(width) * height * 3 * sizeof(uint16_t);
    addLong(ifd0, 0x0117, pixelByteCount);                // StripByteCounts
    addShort(ifd0, 0x011C, 1);                            // PlanarConfiguration = 1 (Chunky)

    if (metadata) {
        addString(ifd0, 0x010F, metadata->make);
        addString(ifd0, 0x0110, metadata->model);
        addString(ifd0, 0x0131, metadata->software);
        addString(ifd0, 0x0132, metadata->dateTimeOriginal);
        addLong(ifd0, 0x8769, 0); // Exif IFD Pointer

        // Exif SubIFD
        uint32_t expDen = metadata->exposureTime > 0 ? static_cast<uint32_t>(std::round(1.0 / metadata->exposureTime)) : 250;
        addRational(exifSubIFD, 0x829A, 1, expDen); // ExposureTime
        uint32_t fNum = static_cast<uint32_t>(std::round(metadata->fNumber * 10.0));
        addRational(exifSubIFD, 0x829D, fNum, 10); // FNumber
        addShort(exifSubIFD, 0x8827, static_cast<uint16_t>(metadata->isoSpeed)); // ISO
        addString(exifSubIFD, 0x9003, metadata->dateTimeOriginal);
        uint32_t focalNum = static_cast<uint32_t>(std::round(metadata->focalLength * 10.0));
        addRational(exifSubIFD, 0x920A, focalNum, 10); // FocalLength
        addString(exifSubIFD, 0xA434, metadata->lensModel);
    }

    std::sort(ifd0.begin(), ifd0.end(), [](const IFDEntry& a, const IFDEntry& b){ return a.tag < b.tag; });
    std::sort(exifSubIFD.begin(), exifSubIFD.end(), [](const IFDEntry& a, const IFDEntry& b){ return a.tag < b.tag; });

    uint32_t ifd0Offset = 8;
    uint32_t ifd0Size = 2 + static_cast<uint32_t>(ifd0.size()) * 12 + 4;
    uint32_t exifOffset = ifd0Offset + ifd0Size;
    uint32_t exifSize = metadata ? (2 + static_cast<uint32_t>(exifSubIFD.size()) * 12 + 4) : 0;
    uint32_t dataOffset = exifOffset + exifSize;

    // Resolve pointers
    for (auto& entry : ifd0) {
        if (entry.tag == 0x8769) {
            entry.valueOrOffset = exifOffset;
        }
    }

    std::vector<uint8_t> ifdBody;
    std::vector<uint8_t> dataHeap;

    auto appendIFD = [&](const std::vector<IFDEntry>& list, uint32_t nextOffset) {
        writeU16LE(ifdBody, static_cast<uint16_t>(list.size()));
        for (const auto& entry : list) {
            writeU16LE(ifdBody, entry.tag);
            writeU16LE(ifdBody, entry.type);
            writeU32LE(ifdBody, entry.count);
            if (entry.data.empty()) {
                writeU32LE(ifdBody, entry.valueOrOffset);
            } else {
                uint32_t offset = dataOffset + static_cast<uint32_t>(dataHeap.size());
                writeU32LE(ifdBody, offset);
                dataHeap.insert(dataHeap.end(), entry.data.begin(), entry.data.end());
                if (entry.data.size() % 2 != 0) dataHeap.push_back(0);
            }
        }
        writeU32LE(ifdBody, nextOffset);
    };

    appendIFD(ifd0, 0);
    if (metadata) {
        appendIFD(exifSubIFD, 0);
    }

    uint32_t pixelDataOffset = dataOffset + static_cast<uint32_t>(dataHeap.size());
    // Align to 4 bytes
    while (pixelDataOffset % 4 != 0) {
        dataHeap.push_back(0);
        pixelDataOffset++;
    }

    // Now update StripOffsets in ifdBody
    for (size_t i = 0; i < ifd0.size(); ++i) {
        if (ifd0[i].tag == 0x0111) {
            // Offset in ifdBody: 2 + i * 12 + 8
            size_t pos = 2 + i * 12 + 8;
            if (pos + 4 <= ifdBody.size()) {
                ifdBody[pos + 0] = static_cast<uint8_t>(pixelDataOffset & 0xFF);
                ifdBody[pos + 1] = static_cast<uint8_t>((pixelDataOffset >> 8) & 0xFF);
                ifdBody[pos + 2] = static_cast<uint8_t>((pixelDataOffset >> 16) & 0xFF);
                ifdBody[pos + 3] = static_cast<uint8_t>((pixelDataOffset >> 24) & 0xFF);
            }
            break;
        }
    }

    tiff.insert(tiff.end(), ifdBody.begin(), ifdBody.end());
    tiff.insert(tiff.end(), dataHeap.begin(), dataHeap.end());

    std::ofstream file(filePath, std::ios::binary);
    if (!file) return false;
    file.write(reinterpret_cast<const char*>(tiff.data()), tiff.size());
    file.write(reinterpret_cast<const char*>(rgb16Data), pixelByteCount);
    return true;
}

bool ImageWriter::writeLinearDNG(const std::string& filePath,
                                 const uint16_t* rgb16Data,
                                 int32_t width, int32_t height,
                                 const ExifMetadata* metadata) {
    if (!rgb16Data || width <= 0 || height <= 0) return false;

    std::vector<uint8_t> tiff;
    writeU16LE(tiff, 0x4949);
    writeU16LE(tiff, 0x002A);
    writeU32LE(tiff, 8);

    std::vector<IFDEntry> ifd0;
    std::vector<IFDEntry> exifSubIFD;

    auto addShort = [](std::vector<IFDEntry>& list, uint16_t tag, uint16_t val) {
        IFDEntry e; e.tag = tag; e.type = 3; e.count = 1; e.valueOrOffset = val;
        list.push_back(e);
    };
    auto addLong = [](std::vector<IFDEntry>& list, uint16_t tag, uint32_t val) {
        IFDEntry e; e.tag = tag; e.type = 4; e.count = 1; e.valueOrOffset = val;
        list.push_back(e);
    };
    auto addShort3 = [](std::vector<IFDEntry>& list, uint16_t tag, uint16_t v0, uint16_t v1, uint16_t v2) {
        IFDEntry e; e.tag = tag; e.type = 3; e.count = 3; e.valueOrOffset = 0;
        e.data.resize(6);
        e.data[0] = static_cast<uint8_t>(v0 & 0xFF); e.data[1] = static_cast<uint8_t>((v0 >> 8) & 0xFF);
        e.data[2] = static_cast<uint8_t>(v1 & 0xFF); e.data[3] = static_cast<uint8_t>((v1 >> 8) & 0xFF);
        e.data[4] = static_cast<uint8_t>(v2 & 0xFF); e.data[5] = static_cast<uint8_t>((v2 >> 8) & 0xFF);
        list.push_back(e);
    };
    auto addString = [](std::vector<IFDEntry>& list, uint16_t tag, const std::string& str) {
        IFDEntry e; e.tag = tag; e.type = 2; e.count = static_cast<uint32_t>(str.length() + 1);
        if (e.count <= 4) {
            e.valueOrOffset = 0;
            std::memcpy(&e.valueOrOffset, str.c_str(), str.length() + 1);
        } else {
            e.data.assign(str.c_str(), str.c_str() + str.length() + 1);
            e.valueOrOffset = 0;
        }
        list.push_back(e);
    };
    auto addBytes4 = [](std::vector<IFDEntry>& list, uint16_t tag, uint8_t b0, uint8_t b1, uint8_t b2, uint8_t b3) {
        IFDEntry e; e.tag = tag; e.type = 1; e.count = 4;
        e.valueOrOffset = static_cast<uint32_t>(b0) | (static_cast<uint32_t>(b1) << 8) |
                          (static_cast<uint32_t>(b2) << 16) | (static_cast<uint32_t>(b3) << 24);
        list.push_back(e);
    };
    auto addRational = [](std::vector<IFDEntry>& list, uint16_t tag, uint32_t num, uint32_t den) {
        IFDEntry e; e.tag = tag; e.type = 5; e.count = 1; e.valueOrOffset = 0;
        e.data.resize(8);
        e.data[0] = static_cast<uint8_t>(num & 0xFF); e.data[1] = static_cast<uint8_t>((num >> 8) & 0xFF);
        e.data[2] = static_cast<uint8_t>((num >> 16) & 0xFF); e.data[3] = static_cast<uint8_t>((num >> 24) & 0xFF);
        e.data[4] = static_cast<uint8_t>(den & 0xFF); e.data[5] = static_cast<uint8_t>((den >> 8) & 0xFF);
        e.data[6] = static_cast<uint8_t>((den >> 16) & 0xFF); e.data[7] = static_cast<uint8_t>((den >> 24) & 0xFF);
        list.push_back(e);
    };
    auto addSRationals = [](std::vector<IFDEntry>& list, uint16_t tag, const std::vector<std::pair<int32_t, int32_t>>& values) {
        IFDEntry e; e.tag = tag; e.type = 10; e.count = static_cast<uint32_t>(values.size()); e.valueOrOffset = 0;
        e.data.resize(values.size() * 8);
        for (size_t i = 0; i < values.size(); ++i) {
            uint32_t num = static_cast<uint32_t>(values[i].first);
            uint32_t den = static_cast<uint32_t>(values[i].second);
            e.data[i * 8 + 0] = static_cast<uint8_t>(num & 0xFF);
            e.data[i * 8 + 1] = static_cast<uint8_t>((num >> 8) & 0xFF);
            e.data[i * 8 + 2] = static_cast<uint8_t>((num >> 16) & 0xFF);
            e.data[i * 8 + 3] = static_cast<uint8_t>((num >> 24) & 0xFF);
            e.data[i * 8 + 4] = static_cast<uint8_t>(den & 0xFF);
            e.data[i * 8 + 5] = static_cast<uint8_t>((den >> 8) & 0xFF);
            e.data[i * 8 + 6] = static_cast<uint8_t>((den >> 16) & 0xFF);
            e.data[i * 8 + 7] = static_cast<uint8_t>((den >> 24) & 0xFF);
        }
        list.push_back(e);
    };

    addLong(ifd0, 0x0100, static_cast<uint32_t>(width));
    addLong(ifd0, 0x0101, static_cast<uint32_t>(height));
    addShort3(ifd0, 0x0102, 16, 16, 16);
    addShort(ifd0, 0x0103, 1);
    addShort(ifd0, 0x0106, 34892); // PhotometricInterpretation: LinearRaw
    addLong(ifd0, 0x0111, 0);
    addShort(ifd0, 0x0115, 3);
    addLong(ifd0, 0x0116, static_cast<uint32_t>(height));
    uint32_t pixelByteCount = static_cast<uint32_t>(width) * height * 3 * sizeof(uint16_t);
    addLong(ifd0, 0x0117, pixelByteCount);
    addShort(ifd0, 0x011C, 1);

    // Standard DNG tags
    addBytes4(ifd0, 0xC612, 1, 4, 0, 0); // DNGVersion 1.4.0.0
    addBytes4(ifd0, 0xC613, 1, 1, 0, 0); // DNGBackwardVersion 1.1.0.0
    addString(ifd0, 0xC614, metadata ? metadata->model : "LightRumor DNG");
    addShort(ifd0, 0xC65A, 21); // CalibrationIlluminant1: D65
    // ColorMatrix1: 3x3 sRGB to XYZ identity mapping for linear representation
    std::vector<std::pair<int32_t, int32_t>> colorMatrix1 = {
        {10000, 10000}, {0, 10000}, {0, 10000},
        {0, 10000}, {10000, 10000}, {0, 10000},
        {0, 10000}, {0, 10000}, {10000, 10000}
    };
    addSRationals(ifd0, 0xC621, colorMatrix1);

    if (metadata) {
        addString(ifd0, 0x010F, metadata->make);
        addString(ifd0, 0x0110, metadata->model);
        addString(ifd0, 0x0131, metadata->software);
        addString(ifd0, 0x0132, metadata->dateTimeOriginal);
        addLong(ifd0, 0x8769, 0);

        uint32_t expDen = metadata->exposureTime > 0 ? static_cast<uint32_t>(std::round(1.0 / metadata->exposureTime)) : 250;
        addRational(exifSubIFD, 0x829A, 1, expDen);
        uint32_t fNum = static_cast<uint32_t>(std::round(metadata->fNumber * 10.0));
        addRational(exifSubIFD, 0x829D, fNum, 10);
        addShort(exifSubIFD, 0x8827, static_cast<uint16_t>(metadata->isoSpeed));
        addString(exifSubIFD, 0x9003, metadata->dateTimeOriginal);
        uint32_t focalNum = static_cast<uint32_t>(std::round(metadata->focalLength * 10.0));
        addRational(exifSubIFD, 0x920A, focalNum, 10);
        addString(exifSubIFD, 0xA434, metadata->lensModel);
    }

    std::sort(ifd0.begin(), ifd0.end(), [](const IFDEntry& a, const IFDEntry& b){ return a.tag < b.tag; });
    std::sort(exifSubIFD.begin(), exifSubIFD.end(), [](const IFDEntry& a, const IFDEntry& b){ return a.tag < b.tag; });

    uint32_t ifd0Offset = 8;
    uint32_t ifd0Size = 2 + static_cast<uint32_t>(ifd0.size()) * 12 + 4;
    uint32_t exifOffset = ifd0Offset + ifd0Size;
    uint32_t exifSize = metadata ? (2 + static_cast<uint32_t>(exifSubIFD.size()) * 12 + 4) : 0;
    uint32_t dataOffset = exifOffset + exifSize;

    for (auto& entry : ifd0) {
        if (entry.tag == 0x8769) {
            entry.valueOrOffset = exifOffset;
        }
    }

    std::vector<uint8_t> ifdBody;
    std::vector<uint8_t> dataHeap;

    auto appendIFD = [&](const std::vector<IFDEntry>& list, uint32_t nextOffset) {
        writeU16LE(ifdBody, static_cast<uint16_t>(list.size()));
        for (const auto& entry : list) {
            writeU16LE(ifdBody, entry.tag);
            writeU16LE(ifdBody, entry.type);
            writeU32LE(ifdBody, entry.count);
            if (entry.data.empty()) {
                writeU32LE(ifdBody, entry.valueOrOffset);
            } else {
                uint32_t offset = dataOffset + static_cast<uint32_t>(dataHeap.size());
                writeU32LE(ifdBody, offset);
                dataHeap.insert(dataHeap.end(), entry.data.begin(), entry.data.end());
                if (entry.data.size() % 2 != 0) dataHeap.push_back(0);
            }
        }
        writeU32LE(ifdBody, nextOffset);
    };

    appendIFD(ifd0, 0);
    if (metadata) {
        appendIFD(exifSubIFD, 0);
    }

    uint32_t pixelDataOffset = dataOffset + static_cast<uint32_t>(dataHeap.size());
    while (pixelDataOffset % 4 != 0) {
        dataHeap.push_back(0);
        pixelDataOffset++;
    }

    for (size_t i = 0; i < ifd0.size(); ++i) {
        if (ifd0[i].tag == 0x0111) {
            size_t pos = 2 + i * 12 + 8;
            if (pos + 4 <= ifdBody.size()) {
                ifdBody[pos + 0] = static_cast<uint8_t>(pixelDataOffset & 0xFF);
                ifdBody[pos + 1] = static_cast<uint8_t>((pixelDataOffset >> 8) & 0xFF);
                ifdBody[pos + 2] = static_cast<uint8_t>((pixelDataOffset >> 16) & 0xFF);
                ifdBody[pos + 3] = static_cast<uint8_t>((pixelDataOffset >> 24) & 0xFF);
            }
            break;
        }
    }

    tiff.insert(tiff.end(), ifdBody.begin(), ifdBody.end());
    tiff.insert(tiff.end(), dataHeap.begin(), dataHeap.end());

    std::ofstream file(filePath, std::ios::binary);
    if (!file) return false;
    file.write(reinterpret_cast<const char*>(tiff.data()), tiff.size());
    file.write(reinterpret_cast<const char*>(rgb16Data), pixelByteCount);
    return true;
}

bool ImageWriter::writeTIFF8(const std::string& filePath,
                             const uint8_t* rgb8Data,
                             int32_t width, int32_t height,
                             const ExifMetadata* metadata) {
    if (!rgb8Data || width <= 0 || height <= 0) return false;

    std::vector<uint8_t> tiff;
    writeU16LE(tiff, 0x4949);
    writeU16LE(tiff, 0x002A);
    writeU32LE(tiff, 8);

    std::vector<IFDEntry> ifd0;
    std::vector<IFDEntry> exifSubIFD;

    auto addShort = [](std::vector<IFDEntry>& list, uint16_t tag, uint16_t val) {
        IFDEntry e; e.tag = tag; e.type = 3; e.count = 1; e.valueOrOffset = val;
        list.push_back(e);
    };
    auto addLong = [](std::vector<IFDEntry>& list, uint16_t tag, uint32_t val) {
        IFDEntry e; e.tag = tag; e.type = 4; e.count = 1; e.valueOrOffset = val;
        list.push_back(e);
    };
    auto addShort3 = [](std::vector<IFDEntry>& list, uint16_t tag, uint16_t v0, uint16_t v1, uint16_t v2) {
        IFDEntry e; e.tag = tag; e.type = 3; e.count = 3; e.valueOrOffset = 0;
        e.data.resize(6);
        e.data[0] = static_cast<uint8_t>(v0 & 0xFF); e.data[1] = static_cast<uint8_t>((v0 >> 8) & 0xFF);
        e.data[2] = static_cast<uint8_t>(v1 & 0xFF); e.data[3] = static_cast<uint8_t>((v1 >> 8) & 0xFF);
        e.data[4] = static_cast<uint8_t>(v2 & 0xFF); e.data[5] = static_cast<uint8_t>((v2 >> 8) & 0xFF);
        list.push_back(e);
    };
    auto addString = [](std::vector<IFDEntry>& list, uint16_t tag, const std::string& str) {
        IFDEntry e; e.tag = tag; e.type = 2; e.count = static_cast<uint32_t>(str.length() + 1);
        if (e.count <= 4) {
            e.valueOrOffset = 0;
            std::memcpy(&e.valueOrOffset, str.c_str(), str.length() + 1);
        } else {
            e.data.assign(str.c_str(), str.c_str() + str.length() + 1);
            e.valueOrOffset = 0;
        }
        list.push_back(e);
    };
    auto addRational = [](std::vector<IFDEntry>& list, uint16_t tag, uint32_t num, uint32_t den) {
        IFDEntry e; e.tag = tag; e.type = 5; e.count = 1; e.valueOrOffset = 0;
        e.data.resize(8);
        e.data[0] = static_cast<uint8_t>(num & 0xFF); e.data[1] = static_cast<uint8_t>((num >> 8) & 0xFF);
        e.data[2] = static_cast<uint8_t>((num >> 16) & 0xFF); e.data[3] = static_cast<uint8_t>((num >> 24) & 0xFF);
        e.data[4] = static_cast<uint8_t>(den & 0xFF); e.data[5] = static_cast<uint8_t>((den >> 8) & 0xFF);
        e.data[6] = static_cast<uint8_t>((den >> 16) & 0xFF); e.data[7] = static_cast<uint8_t>((den >> 24) & 0xFF);
        list.push_back(e);
    };

    addLong(ifd0, 0x0100, static_cast<uint32_t>(width));
    addLong(ifd0, 0x0101, static_cast<uint32_t>(height));
    addShort3(ifd0, 0x0102, 8, 8, 8);
    addShort(ifd0, 0x0103, 1);
    addShort(ifd0, 0x0106, 2);
    addLong(ifd0, 0x0111, 0); // StripOffsets
    addShort(ifd0, 0x0115, 3);
    addLong(ifd0, 0x0116, static_cast<uint32_t>(height));
    uint32_t pixelByteCount = static_cast<uint32_t>(width) * height * 3;
    addLong(ifd0, 0x0117, pixelByteCount);
    addShort(ifd0, 0x011C, 1);

    if (metadata) {
        addString(ifd0, 0x010F, metadata->make);
        addString(ifd0, 0x0110, metadata->model);
        addString(ifd0, 0x0131, metadata->software);
        addString(ifd0, 0x0132, metadata->dateTimeOriginal);
        addLong(ifd0, 0x8769, 0); // Exif IFD Pointer

        uint32_t expDen = metadata->exposureTime > 0 ? static_cast<uint32_t>(std::round(1.0 / metadata->exposureTime)) : 250;
        addRational(exifSubIFD, 0x829A, 1, expDen);
        uint32_t fNum = static_cast<uint32_t>(std::round(metadata->fNumber * 10.0));
        addRational(exifSubIFD, 0x829D, fNum, 10);
        addShort(exifSubIFD, 0x8827, static_cast<uint16_t>(metadata->isoSpeed));
        addString(exifSubIFD, 0x9003, metadata->dateTimeOriginal);
        uint32_t focalNum = static_cast<uint32_t>(std::round(metadata->focalLength * 10.0));
        addRational(exifSubIFD, 0x920A, focalNum, 10);
        addString(exifSubIFD, 0xA434, metadata->lensModel);
    }

    std::sort(ifd0.begin(), ifd0.end(), [](const IFDEntry& a, const IFDEntry& b){ return a.tag < b.tag; });
    std::sort(exifSubIFD.begin(), exifSubIFD.end(), [](const IFDEntry& a, const IFDEntry& b){ return a.tag < b.tag; });

    uint32_t ifd0Offset = 8;
    uint32_t ifd0Size = 2 + static_cast<uint32_t>(ifd0.size()) * 12 + 4;
    uint32_t exifOffset = ifd0Offset + ifd0Size;
    uint32_t exifSize = metadata ? (2 + static_cast<uint32_t>(exifSubIFD.size()) * 12 + 4) : 0;
    uint32_t dataOffset = exifOffset + exifSize;

    for (auto& entry : ifd0) {
        if (entry.tag == 0x8769) {
            entry.valueOrOffset = exifOffset;
        }
    }

    std::vector<uint8_t> ifdBody;
    std::vector<uint8_t> dataHeap;

    auto appendIFD = [&](const std::vector<IFDEntry>& list, uint32_t nextOffset) {
        writeU16LE(ifdBody, static_cast<uint16_t>(list.size()));
        for (const auto& entry : list) {
            writeU16LE(ifdBody, entry.tag);
            writeU16LE(ifdBody, entry.type);
            writeU32LE(ifdBody, entry.count);
            if (entry.data.empty()) {
                writeU32LE(ifdBody, entry.valueOrOffset);
            } else {
                uint32_t offset = dataOffset + static_cast<uint32_t>(dataHeap.size());
                writeU32LE(ifdBody, offset);
                dataHeap.insert(dataHeap.end(), entry.data.begin(), entry.data.end());
                if (entry.data.size() % 2 != 0) dataHeap.push_back(0);
            }
        }
        writeU32LE(ifdBody, nextOffset);
    };

    appendIFD(ifd0, 0);
    if (metadata) {
        appendIFD(exifSubIFD, 0);
    }

    uint32_t pixelDataOffset = dataOffset + static_cast<uint32_t>(dataHeap.size());
    while (pixelDataOffset % 4 != 0) {
        dataHeap.push_back(0);
        pixelDataOffset++;
    }

    for (size_t i = 0; i < ifd0.size(); ++i) {
        if (ifd0[i].tag == 0x0111) {
            size_t pos = 2 + i * 12 + 8;
            if (pos + 4 <= ifdBody.size()) {
                ifdBody[pos + 0] = static_cast<uint8_t>(pixelDataOffset & 0xFF);
                ifdBody[pos + 1] = static_cast<uint8_t>((pixelDataOffset >> 8) & 0xFF);
                ifdBody[pos + 2] = static_cast<uint8_t>((pixelDataOffset >> 16) & 0xFF);
                ifdBody[pos + 3] = static_cast<uint8_t>((pixelDataOffset >> 24) & 0xFF);
            }
            break;
        }
    }

    tiff.insert(tiff.end(), ifdBody.begin(), ifdBody.end());
    tiff.insert(tiff.end(), dataHeap.begin(), dataHeap.end());

    std::ofstream file(filePath, std::ios::binary);
    if (!file) return false;
    file.write(reinterpret_cast<const char*>(tiff.data()), tiff.size());
    file.write(reinterpret_cast<const char*>(rgb8Data), pixelByteCount);
    return true;
}

// Minimal 5x7 ASCII bitmap font (characters 32 to 126)
namespace {
    // 5x7 font data encoded as 5 bytes per glyph (each byte is 1 column of 7 vertical bits)
    const uint8_t FONT_5X7[] = {
        0x00, 0x00, 0x00, 0x00, 0x00, // 32 ' '
        0x00, 0x00, 0x5F, 0x00, 0x00, // 33 '!'
        0x00, 0x07, 0x00, 0x07, 0x00, // 34 '"'
        0x14, 0x7F, 0x14, 0x7F, 0x14, // 35 '#'
        0x24, 0x2A, 0x7F, 0x2A, 0x12, // 36 '$'
        0x23, 0x13, 0x08, 0x64, 0x62, // 37 '%'
        0x36, 0x49, 0x55, 0x22, 0x50, // 38 '&'
        0x00, 0x05, 0x03, 0x00, 0x00, // 39 '\''
        0x00, 0x1C, 0x22, 0x41, 0x00, // 40 '('
        0x00, 0x41, 0x22, 0x1C, 0x00, // 41 ')'
        0x08, 0x2A, 0x1C, 0x2A, 0x08, // 42 '*'
        0x08, 0x08, 0x3E, 0x08, 0x08, // 43 '+'
        0x00, 0x50, 0x30, 0x00, 0x00, // 44 ','
        0x08, 0x08, 0x08, 0x08, 0x08, // 45 '-'
        0x00, 0x60, 0x60, 0x00, 0x00, // 46 '.'
        0x20, 0x10, 0x08, 0x04, 0x02, // 47 '/'
        0x3E, 0x51, 0x49, 0x45, 0x3E, // 48 '0'
        0x00, 0x42, 0x7F, 0x40, 0x00, // 49 '1'
        0x42, 0x61, 0x51, 0x49, 0x46, // 50 '2'
        0x21, 0x41, 0x45, 0x4B, 0x31, // 51 '3'
        0x18, 0x14, 0x12, 0x7F, 0x10, // 52 '4'
        0x27, 0x45, 0x45, 0x45, 0x39, // 53 '5'
        0x3C, 0x4A, 0x49, 0x49, 0x30, // 54 '6'
        0x01, 0x71, 0x09, 0x05, 0x03, // 55 '7'
        0x36, 0x49, 0x49, 0x49, 0x36, // 56 '8'
        0x06, 0x49, 0x49, 0x29, 0x1E, // 57 '9'
        0x00, 0x36, 0x36, 0x00, 0x00, // 58 ':'
        0x00, 0x56, 0x36, 0x00, 0x00, // 59 ';'
        0x08, 0x14, 0x22, 0x41, 0x00, // 60 '<'
        0x14, 0x14, 0x14, 0x14, 0x14, // 61 '='
        0x00, 0x41, 0x22, 0x14, 0x08, // 62 '>'
        0x02, 0x01, 0x51, 0x09, 0x06, // 63 '?'
        0x32, 0x49, 0x79, 0x41, 0x3E, // 64 '@'
        0x7E, 0x11, 0x11, 0x11, 0x7E, // 65 'A'
        0x7F, 0x49, 0x49, 0x49, 0x36, // 66 'B'
        0x3E, 0x41, 0x41, 0x41, 0x22, // 67 'C'
        0x7F, 0x41, 0x41, 0x22, 0x1C, // 68 'D'
        0x7F, 0x49, 0x49, 0x49, 0x41, // 69 'E'
        0x7F, 0x09, 0x09, 0x09, 0x01, // 70 'F'
        0x3E, 0x41, 0x49, 0x49, 0x7A, // 71 'G'
        0x7F, 0x08, 0x08, 0x08, 0x7F, // 72 'H'
        0x00, 0x41, 0x7F, 0x41, 0x00, // 73 'I'
        0x20, 0x40, 0x41, 0x3F, 0x01, // 74 'J'
        0x7F, 0x08, 0x14, 0x22, 0x41, // 75 'K'
        0x7F, 0x40, 0x40, 0x40, 0x40, // 76 'L'
        0x7F, 0x02, 0x0C, 0x02, 0x7F, // 77 'M'
        0x7F, 0x04, 0x08, 0x10, 0x7F, // 78 'N'
        0x3E, 0x41, 0x41, 0x41, 0x3E, // 79 'O'
        0x7F, 0x09, 0x09, 0x09, 0x06, // 80 'P'
        0x3E, 0x41, 0x51, 0x21, 0x5E, // 81 'Q'
        0x7F, 0x09, 0x19, 0x29, 0x46, // 82 'R'
        0x46, 0x49, 0x49, 0x49, 0x31, // 83 'S'
        0x01, 0x01, 0x7F, 0x01, 0x01, // 84 'T'
        0x3F, 0x40, 0x40, 0x40, 0x3F, // 85 'U'
        0x1F, 0x20, 0x40, 0x20, 0x1F, // 86 'V'
        0x3F, 0x40, 0x38, 0x40, 0x3F, // 87 'W'
        0x63, 0x14, 0x08, 0x14, 0x63, // 88 'X'
        0x07, 0x08, 0x70, 0x08, 0x07, // 89 'Y'
        0x61, 0x51, 0x49, 0x45, 0x43, // 90 'Z'
        0x00, 0x7F, 0x41, 0x41, 0x00, // 91 '['
        0x02, 0x04, 0x08, 0x10, 0x20, // 92 '\'
        0x00, 0x41, 0x41, 0x7F, 0x00, // 93 ']'
        0x04, 0x02, 0x01, 0x02, 0x04, // 94 '^'
        0x40, 0x40, 0x40, 0x40, 0x40, // 95 '_'
        0x00, 0x01, 0x02, 0x04, 0x00, // 96 '`'
        0x20, 0x54, 0x54, 0x54, 0x78, // 97 'a'
        0x7F, 0x48, 0x44, 0x44, 0x38, // 98 'b'
        0x38, 0x44, 0x44, 0x44, 0x20, // 99 'c'
        0x38, 0x44, 0x44, 0x48, 0x7F, // 100 'd'
        0x38, 0x54, 0x54, 0x54, 0x18, // 101 'e'
        0x08, 0x7E, 0x09, 0x01, 0x02, // 102 'f'
        0x08, 0x14, 0x54, 0x54, 0x3C, // 103 'g'
        0x7F, 0x08, 0x04, 0x04, 0x78, // 104 'h'
        0x00, 0x44, 0x7D, 0x40, 0x00, // 105 'i'
        0x20, 0x40, 0x44, 0x3D, 0x00, // 106 'j'
        0x7F, 0x10, 0x28, 0x44, 0x00, // 107 'k'
        0x00, 0x41, 0x7F, 0x40, 0x00, // 108 'l'
        0x7C, 0x04, 0x18, 0x04, 0x78, // 109 'm'
        0x7C, 0x08, 0x04, 0x04, 0x78, // 110 'n'
        0x38, 0x44, 0x44, 0x44, 0x38, // 111 'o'
        0x7C, 0x14, 0x14, 0x14, 0x08, // 112 'p'
        0x08, 0x14, 0x14, 0x18, 0x7C, // 113 'q'
        0x7C, 0x08, 0x04, 0x04, 0x08, // 114 'r'
        0x48, 0x54, 0x54, 0x54, 0x20, // 115 's'
        0x04, 0x3F, 0x44, 0x40, 0x20, // 116 't'
        0x3C, 0x40, 0x40, 0x20, 0x7C, // 117 'u'
        0x1C, 0x20, 0x40, 0x20, 0x1C, // 118 'v'
        0x3C, 0x40, 0x30, 0x40, 0x3C, // 119 'w'
        0x44, 0x28, 0x10, 0x28, 0x44, // 120 'x'
        0x0C, 0x50, 0x50, 0x50, 0x3C, // 121 'y'
        0x44, 0x64, 0x54, 0x4C, 0x44, // 122 'z'
        0x00, 0x08, 0x36, 0x41, 0x00, // 123 '{'
        0x00, 0x00, 0x7F, 0x00, 0x00, // 124 '|'
        0x00, 0x41, 0x36, 0x08, 0x00, // 125 '}'
        0x08, 0x08, 0x2A, 0x1C, 0x08  // 126 '~'
    };
} // namespace

bool ImageWriter::renderWatermark8(std::vector<uint8_t>& rgbData,
                                  int32_t& inOutWidth, int32_t& inOutHeight,
                                  const std::string& text,
                                  bool addBottomMargin) {
    if (inOutWidth <= 0 || inOutHeight <= 0) return false;
    size_t expectedSize = static_cast<size_t>(inOutWidth) * inOutHeight * 3;
    if (rgbData.size() < expectedSize) return false;

    const int W = inOutWidth;
    const int H = inOutHeight;

    int scale = std::max(1, W / 1000); // Scale font for high-res images (e.g. 2x for 2048px, 4x for 45MP)
    int charW = 5 * scale;
    int charH = 7 * scale;
    int spacing = 2 * scale;

    int textPixelWidth = static_cast<int>(text.length()) * (charW + spacing);

    if (addBottomMargin) {
        int marginH = std::max(60, static_cast<int>(H * 0.08f));
        int newH = H + marginH;
        std::vector<uint8_t> newBuf(static_cast<size_t>(W) * newH * 3);

        // Copy original image to top
        std::memcpy(newBuf.data(), rgbData.data(), static_cast<size_t>(W) * H * 3);

        // Fill bottom margin with Gallery Obsidian Black (#0F0F12)
        for (int y = H; y < newH; ++y) {
            for (int x = 0; x < W; ++x) {
                size_t idx = (static_cast<size_t>(y) * W + x) * 3;
                newBuf[idx + 0] = 0x0F;
                newBuf[idx + 1] = 0x0F;
                newBuf[idx + 2] = 0x12;
            }
        }

        // Draw centered watermark in margin
        int startX = std::max(20, (W - textPixelWidth) / 2);
        int startY = H + (marginH - charH) / 2;

        auto drawPixel = [&](int px, int py, uint8_t r, uint8_t g, uint8_t b) {
            if (px >= 0 && px < W && py >= 0 && py < newH) {
                size_t idx = (static_cast<size_t>(py) * W + px) * 3;
                newBuf[idx + 0] = r;
                newBuf[idx + 1] = g;
                newBuf[idx + 2] = b;
            }
        };

        for (size_t c = 0; c < text.length(); ++c) {
            char ch = text[c];
            int glyphIdx = std::clamp(static_cast<int>(ch) - 32, 0, 94);
            const uint8_t* glyph = &FONT_5X7[glyphIdx * 5];

            for (int col = 0; col < 5; ++col) {
                uint8_t bits = glyph[col];
                for (int row = 0; row < 7; ++row) {
                    if ((bits >> row) & 1) {
                        for (int sy = 0; sy < scale; ++sy) {
                            for (int sx = 0; sx < scale; ++sx) {
                                drawPixel(startX + col * scale + sx, startY + row * scale + sy,
                                          0xE5, 0xE5, 0xEA); // Warm silver typography
                            }
                        }
                    }
                }
            }
            startX += charW + spacing;
        }

        rgbData = std::move(newBuf);
        inOutHeight = newH;
    } else {
        // Overlay directly in bottom-right corner with 24px padding
        int startX = std::max(20, W - textPixelWidth - 30 * scale);
        int startY = H - charH - 24 * scale;

        auto blendPixel = [&](int px, int py, uint8_t r, uint8_t g, uint8_t b) {
            if (px >= 0 && px < W && py >= 0 && py < H) {
                size_t idx = (static_cast<size_t>(py) * W + px) * 3;
                int curR = static_cast<int>(rgbData[idx + 0]);
                int curG = static_cast<int>(rgbData[idx + 1]);
                int curB = static_cast<int>(rgbData[idx + 2]);
                int inR = static_cast<int>(r);
                int inG = static_cast<int>(g);
                int inB = static_cast<int>(b);
                rgbData[idx + 0] = static_cast<uint8_t>((curR * 30 + inR * 225) / 255);
                rgbData[idx + 1] = static_cast<uint8_t>((curG * 30 + inG * 225) / 255);
                rgbData[idx + 2] = static_cast<uint8_t>((curB * 30 + inB * 225) / 255);
            }
        };

        for (size_t c = 0; c < text.length(); ++c) {
            char ch = text[c];
            int glyphIdx = std::clamp(static_cast<int>(ch) - 32, 0, 94);
            const uint8_t* glyph = &FONT_5X7[glyphIdx * 5];

            for (int col = 0; col < 5; ++col) {
                uint8_t bits = glyph[col];
                for (int row = 0; row < 7; ++row) {
                    if ((bits >> row) & 1) {
                        for (int sy = 0; sy < scale; ++sy) {
                            for (int sx = 0; sx < scale; ++sx) {
                                blendPixel(startX + col * scale + sx, startY + row * scale + sy,
                                           0xFF, 0xFF, 0xFF);
                            }
                        }
                    }
                }
            }
            startX += charW + spacing;
        }
    }

    return true;
}

bool ImageWriter::writeWebP(const std::string& filePath,
                            const uint8_t* rgbData,
                            int32_t width, int32_t height,
                            int32_t quality,
                            const ExifMetadata* metadata) {
    if (!rgbData || width <= 0 || height <= 0) return false;

#if defined(LIGHT_RUMOR_ENABLE_WEBP)
    uint8_t* webpOutput = nullptr;
    size_t webpSize = 0;
    if (quality >= 100) {
        webpSize = WebPEncodeLosslessRGB(rgbData, width, height, width * 3, &webpOutput);
    } else {
        float q = std::clamp(static_cast<float>(quality), 1.0f, 100.0f);
        webpSize = WebPEncodeRGB(rgbData, width, height, width * 3, q, &webpOutput);
    }
    if (webpSize == 0 || !webpOutput) {
        std::cerr << "[ImageWriter] WebPEncode failed." << std::endl;
        return false;
    }

    if (metadata && webpSize >= 12 && std::memcmp(webpOutput, "RIFF", 4) == 0) {
        std::vector<uint8_t> exif = buildExifPayload(*metadata);
        const uint8_t* tiffPayload = exif.data() + 6;
        size_t tiffSize = exif.size() - 6;

        std::vector<uint8_t> webpExtended;
        webpExtended.reserve(webpSize + tiffSize + 64);
        writeU16LE(webpExtended, 0x4952); // 'RI'
        writeU16LE(webpExtended, 0x4646); // 'FF'
        writeU32LE(webpExtended, 0); // Placeholder
        writeU16LE(webpExtended, 0x4557); // 'WE'
        writeU16LE(webpExtended, 0x5042); // 'BP'

        // VP8X chunk
        writeU16LE(webpExtended, 0x5056); // 'VP'
        writeU16LE(webpExtended, 0x5838); // '8X'
        writeU32LE(webpExtended, 10);
        uint32_t flags = (1 << 3); // EXIF flag
        writeU32LE(webpExtended, flags);
        uint32_t cW = static_cast<uint32_t>(width - 1);
        uint32_t cH = static_cast<uint32_t>(height - 1);
        webpExtended.push_back(static_cast<uint8_t>(cW & 0xFF));
        webpExtended.push_back(static_cast<uint8_t>((cW >> 8) & 0xFF));
        webpExtended.push_back(static_cast<uint8_t>((cW >> 16) & 0xFF));
        webpExtended.push_back(static_cast<uint8_t>(cH & 0xFF));
        webpExtended.push_back(static_cast<uint8_t>((cH >> 8) & 0xFF));
        webpExtended.push_back(static_cast<uint8_t>((cH >> 16) & 0xFF));

        // Copy bitstream chunk from webpOutput (skip RIFF header 12 bytes)
        // Per WebP Container Specification, image bitstream MUST precede metadata chunks (EXIF, XMP).
        webpExtended.insert(webpExtended.end(), webpOutput + 12, webpOutput + webpSize);

        // EXIF chunk
        writeU16LE(webpExtended, 0x5845); // 'EX'
        writeU16LE(webpExtended, 0x4649); // 'IF'
        writeU32LE(webpExtended, static_cast<uint32_t>(tiffSize));
        webpExtended.insert(webpExtended.end(), tiffPayload, tiffPayload + tiffSize);
        if (tiffSize % 2 != 0) webpExtended.push_back(0);

        uint32_t totalRiff = static_cast<uint32_t>(webpExtended.size() - 8);
        webpExtended[4] = static_cast<uint8_t>(totalRiff & 0xFF);
        webpExtended[5] = static_cast<uint8_t>((totalRiff >> 8) & 0xFF);
        webpExtended[6] = static_cast<uint8_t>((totalRiff >> 16) & 0xFF);
        webpExtended[7] = static_cast<uint8_t>((totalRiff >> 24) & 0xFF);

        std::ofstream out(filePath, std::ios::binary);
        if (!out) {
            WebPFree(webpOutput);
            return false;
        }
        out.write(reinterpret_cast<const char*>(webpExtended.data()), webpExtended.size());
        WebPFree(webpOutput);
        return true;
    }

    std::ofstream out(filePath, std::ios::binary);
    if (!out) {
        WebPFree(webpOutput);
        return false;
    }
    out.write(reinterpret_cast<const char*>(webpOutput), webpSize);
    WebPFree(webpOutput);
    return true;
#else
    // Fallback: write valid RIFF/WEBP container
    std::vector<uint8_t> webp;
    writeU16LE(webp, 0x4952); // 'RI'
    writeU16LE(webp, 0x4646); // 'FF'
    writeU32LE(webp, 0); // Placeholder
    writeU16LE(webp, 0x4557); // 'WE'
    writeU16LE(webp, 0x5042); // 'BP'

    // VP8X Chunk
    writeU16LE(webp, 0x5056); // 'VP'
    writeU16LE(webp, 0x5838); // '8X'
    writeU32LE(webp, 10);
    uint32_t flags = (metadata != nullptr) ? (1 << 3) : 0;
    writeU32LE(webp, flags);
    uint32_t cW = static_cast<uint32_t>(width - 1);
    uint32_t cH = static_cast<uint32_t>(height - 1);
    webp.push_back(static_cast<uint8_t>(cW & 0xFF));
    webp.push_back(static_cast<uint8_t>((cW >> 8) & 0xFF));
    webp.push_back(static_cast<uint8_t>((cW >> 16) & 0xFF));
    webp.push_back(static_cast<uint8_t>(cH & 0xFF));
    webp.push_back(static_cast<uint8_t>((cH >> 8) & 0xFF));
    webp.push_back(static_cast<uint8_t>((cH >> 16) & 0xFF));

    // VP8 chunk (bitstream comes before EXIF)
    writeU16LE(webp, 0x5056); // 'VP'
    writeU16LE(webp, 0x2038); // '8 '
    writeU32LE(webp, 10);
    uint32_t frameTag = (0) | (0 << 1) | (1 << 4);
    webp.push_back(static_cast<uint8_t>(frameTag & 0xFF));
    webp.push_back(static_cast<uint8_t>((frameTag >> 8) & 0xFF));
    webp.push_back(static_cast<uint8_t>((frameTag >> 16) & 0xFF));
    webp.push_back(0x9D);
    webp.push_back(0x01);
    webp.push_back(0x2A);
    uint16_t wTag = static_cast<uint16_t>(width & 0x3FFF);
    webp.push_back(static_cast<uint8_t>(wTag & 0xFF));
    webp.push_back(static_cast<uint8_t>((wTag >> 8) & 0xFF));
    uint16_t hTag = static_cast<uint16_t>(height & 0x3FFF);
    webp.push_back(static_cast<uint8_t>(hTag & 0xFF));
    webp.push_back(static_cast<uint8_t>((hTag >> 8) & 0xFF));

    if (metadata) {
        std::vector<uint8_t> exif = buildExifPayload(*metadata);
        const uint8_t* tiffPayload = exif.data() + 6;
        size_t tiffSize = exif.size() - 6;

        writeU16LE(webp, 0x5845); // 'EX'
        writeU16LE(webp, 0x4649); // 'IF'
        writeU32LE(webp, static_cast<uint32_t>(tiffSize));
        webp.insert(webp.end(), tiffPayload, tiffPayload + tiffSize);
        if (tiffSize % 2 != 0) webp.push_back(0);
    }

    uint32_t totalRiff = static_cast<uint32_t>(webp.size() - 8);
    webp[4] = static_cast<uint8_t>(totalRiff & 0xFF);
    webp[5] = static_cast<uint8_t>((totalRiff >> 8) & 0xFF);
    webp[6] = static_cast<uint8_t>((totalRiff >> 16) & 0xFF);
    webp[7] = static_cast<uint8_t>((totalRiff >> 24) & 0xFF);

    std::ofstream out(filePath, std::ios::binary);
    if (!out) return false;
    out.write(reinterpret_cast<const char*>(webp.data()), webp.size());
    return true;
#endif
}

} // namespace lightrumor
