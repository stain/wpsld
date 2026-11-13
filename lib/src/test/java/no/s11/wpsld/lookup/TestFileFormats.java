/*
 * Copyright 2026 The University of Manchester, UK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package no.s11.wpsld.lookup;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

public class TestFileFormats {
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private Path write(String name, byte[] content) throws Exception {
		return Files.write(temp.getRoot().toPath().resolve(name), content);
	}

	private static byte[] hdf5At(int offset) {
		byte[] bytes = new byte[offset + 64];
		byte[] signature = { (byte) 0x89, 'H', 'D', 'F', '\r', '\n', 0x1a, '\n' };
		System.arraycopy(signature, 0, bytes, offset, signature.length);
		return bytes;
	}

	/** By content, whatever the name says */
	@Test
	public void byContent() throws Exception {
		FileFormats formats = FileFormats.bundled();
		FileFormats.Detected pdf = formats.detect(write("scan.bin", "%PDF-1.4\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1)));
		assertEquals("application/pdf", pdf.mediaType());
		assertEquals("Portable Document Format", pdf.name().orElseThrow());
		// PRONOM has PDF's versions, not PDF as such
		assertTrue(pdf.pronom().isEmpty());
		assertEquals(FileFormats.HDF5, formats.detect(write("run1.h5", hdf5At(0))).mediaType());
		// After a user block, as some instruments write
		assertEquals(FileFormats.HDF5, formats.detect(write("run2.dat", hdf5At(512))).mediaType());
	}

	/** An instrument's .dat: text or not; never a guess from the extension */
	@Test
	public void unknown() throws Exception {
		FileFormats formats = FileFormats.bundled();
		byte[] noise = new byte[4096];
		new Random(42).nextBytes(noise);
		FileFormats.Detected bin = formats.detect(write("signal.dat", noise));
		assertEquals(FileFormats.OCTET_STREAM, bin.mediaType());
		assertTrue(bin.pronom().isEmpty());
		assertEquals("text/plain", formats.detect(write("log.dat", "t=0 v=1.2\nt=1 v=1.3\n".getBytes())).mediaType());
	}

	/** Linked to PRONOM where only one format fits, and described in the crate */
	@Test
	public void addTo() throws Exception {
		Crate crate = new Crate();
		Entity csv = crate.add("File");
		crate.rename(csv, "counts.csv");
		Path file = write("counts.csv", "species,count\nrobin,3\n".getBytes());
		FileFormats.Detected detected = FileFormats.bundled().addTo(crate, csv, file).orElseThrow();
		assertEquals("text/csv", detected.mediaType());
		assertEquals("x-fmt/18", detected.pronom().orElseThrow().puid());
		assertEquals("text/csv", csv.getAll("encodingFormat").get(0).asText());
		String iri = csv.getAll("encodingFormat").get(1).get("@id").asText();
		assertTrue(crate.get(iri).orElseThrow().getTypes().containsAll(PRONOMFormats.TYPES));
		// Not again, once it has one
		assertTrue(FileFormats.bundled().addTo(crate, csv, file).isEmpty());
		assertEquals(2, csv.getAll("encodingFormat").size());
	}

	/** Corrections to Tika: registered names, text it doesn't tell apart, NASA's CDF */
	@Test
	public void corrections() throws Exception {
		FileFormats formats = FileFormats.bundled();
		assertEquals("application/vnd.apache.parquet",
				formats.detect(write("table.parquet", java.util.Arrays.copyOf("PAR1".getBytes(), 64))).mediaType());
		assertEquals("application/yaml", formats.detect(write("config.yaml", "a: 1\nb: [2, 3]\n".getBytes())).mediaType());
		assertEquals("application/ld+json", formats.detect(write("crate.jsonld", "{\"@context\": {}}".getBytes())).mediaType());
		assertEquals("application/x-ipynb+json",
				formats.detect(write("notebook.ipynb", "{\"cells\": [], \"nbformat\": 4}".getBytes())).mediaType());
		byte[] cdf = new byte[64];
		cdf[0] = (byte) 0xcd;
		cdf[1] = (byte) 0xf3;
		cdf[3] = 1;
		assertEquals(FileFormats.NASA_CDF, formats.detect(write("wind.cdf", cdf)).mediaType());
		byte[] netcdf = java.util.Arrays.copyOf("CDF".getBytes(), 64);
		netcdf[3] = 1;
		assertEquals("application/x-netcdf", formats.detect(write("climate.cdf", netcdf)).mediaType());
	}

	private static byte[] at(int size, int offset, byte[] head) {
		byte[] bytes = new byte[Math.max(size, offset + head.length)];
		System.arraycopy(head, 0, bytes, offset, head.length);
		return bytes;
	}

	private static byte[] bytes(int... values) {
		byte[] bytes = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			bytes[i] = (byte) values[i];
		}
		return bytes;
	}

	private static byte[] text(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Formats of research data, each a file of its first bytes only: its
	 * media type, and PRONOM format where only one fits (else "")
	 */
	@Test
	public void researchFormats() throws Exception {
		Object[][] cases = {
				// Tables and statistics
				{ "counts.csv", text("species,count\nrobin,3\n"), "text/csv", "x-fmt/18" },
				{ "counts.tsv", text("species\tcount\nrobin\t3\n"), "text/tab-separated-values", "x-fmt/13" },
				{ "table.parquet", at(64, 0, text("PAR1")), "application/vnd.apache.parquet", "" },
				{ "table.arrow", at(64, 0, text("ARROW1")), "application/vnd.apache.arrow.file", "" },
				{ "table.feather", at(64, 0, text("ARROW1")), "application/vnd.apache.arrow.file", "" },
				{ "db.sqlite", at(64, 0, text("SQLite format 3\0")), "application/x-sqlite3", "fmt/729" },
				{ "survey.sav", at(64, 0, text("$FL2")), "application/x-spss-sav", "" },
				{ "survey.dta", text("<stata_dta><header><release>118</release>"), "application/x-stata-dta; version=14", "" },
				{ "survey.sas7bdat", at(1024, 12, bytes(0xc2, 0xea, 0x81, 0x60, 0xb3, 0x14, 0x11, 0xcf, 0xbd, 0x92, 0x08,
						0x00, 0x09, 0xc7, 0x31, 0x8c, 0x18, 0x1f, 0x10, 0x11)), "application/x-sas-data", "" },
				// Arrays and earth sciences
				{ "run.h5", at(600, 0, bytes(0x89, 'H', 'D', 'F', '\r', '\n', 0x1a, '\n')), FileFormats.HDF5, "" },
				{ "old.hdf", at(64, 0, bytes(0x0e, 0x03, 0x13, 0x01)), "application/x-hdf", "" },
				{ "classic.nc", at(64, 0, bytes('C', 'D', 'F', 1)), "application/x-netcdf", "" },
				{ "offset64.nc", at(64, 0, bytes('C', 'D', 'F', 2)), "application/x-netcdf", "" },
				{ "cdf5.nc", at(64, 0, bytes('C', 'D', 'F', 5)), "application/x-netcdf", "" },
				// Not CD Audio, whose media type PRONOM gives as application/x-cdf too
				{ "wind.cdf", at(64, 0, bytes(0xcd, 0xf3, 0, 1)), FileFormats.NASA_CDF, "" },
				{ "forecast.grib2", at(64, 0, text("GRIB")), "application/grib", "" },
				{ "obs.bufr", at(64, 0, text("BUFR")), "application/bufr", "" },
				{ "image.fits", at(2880, 0, text(String.format("%-80s", "SIMPLE  =                    T"))),
						"application/fits", "x-fmt/383" },
				{ "results.mat", at(128, 0, text(String.format("%-116s", "MATLAB 5.0 MAT-file, Platform: GLNXA64"))),
						"application/x-matlab-data", "" },
				// Geography
				{ "rivers.shp", at(100, 0, bytes(0, 0, 0x27, 0x0a)), "application/x-shapefile", "" },
				{ "places.geojson", text("{\"type\": \"FeatureCollection\", \"features\": []}"), "application/geo+json",
						"fmt/1367" },
				{ "places.kml", text("<?xml version=\"1.0\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\"></kml>"),
						"application/vnd.google-earth.kml+xml", "fmt/244" },
				{ "points.las", at(256, 0, text("LASF")), "application/x-asprs", "" },
				// Medicine and life sciences
				{ "scan.dcm", at(200, 128, text("DICM")), "application/dicom", "fmt/574" },
				{ "seqs.fasta", text(">seq1 test\nACGTACGT\n"), "text/plain", "" },
				{ "reads.fastq", text("@read1\nACGT\n+\nIIII\n"), "text/plain", "" },
				// Not a vCard, as Tika takes .vcf for
				{ "calls.vcf", text("##fileformat=VCFv4.2\n#CHROM\tPOS\n"), "text/plain", "" },
				// Gzip, but not just a GZIP file: no PRONOM format
				{ "aln.bam", at(64, 0, bytes(0x1f, 0x8b, 8, 4)), "application/gzip", "" },
				{ "data.gz", at(64, 0, bytes(0x1f, 0x8b, 8, 0)), "application/gzip", "x-fmt/266" },
				{ "protein.pdb", text("HEADER    HYDROLASE\nATOM      1  N   MET A   1\n"), "text/plain", "" },
				// An electron microscopy map, not a MARC library record, as Tika takes .mrc for
				{ "map.mrc", at(1100, 208, text("MAP ")), FileFormats.OCTET_STREAM, "" },
				// Images, sound and video
				{ "figure.png", at(64, 0, bytes(0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n')), "image/png", "" },
				{ "micrograph.tif", at(64, 0, bytes('I', 'I', 42, 0)), "image/tiff", "" },
				{ "image.jp2", at(64, 0, bytes(0, 0, 0, 0x0c, 'j', 'P', ' ', ' ', 0x0d, 0x0a, 0x87, 0x0a)), "image/jp2",
						"x-fmt/392" },
				{ "figure.svg", text("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"),
						"image/svg+xml", "" },
				{ "sound.wav", at(64, 0, text("RIFF\0\0\0\0WAVEfmt ")), "audio/vnd.wave", "" },
				{ "call.flac", at(64, 0, text("fLaC")), "audio/x-flac", "" },
				{ "video.mp4", at(64, 4, text("ftypisom")), "video/mp4", "fmt/199" },
				// Documents, code and notebooks
				{ "paper.pdf", text("%PDF-1.7\n"), "application/pdf", "" },
				{ "README.md", text("# Title\n\nSome *text*.\n"), "text/markdown", "fmt/1149" },
				{ "analysis.py", text("import numpy as np\nprint(1)\n"), "text/x-python", "" },
				{ "analysis.R", text("x <- c(1, 2)\nprint(x)\n"), "text/x-rsrc", "" },
				{ "notebook.ipynb", text("{\"cells\": [], \"nbformat\": 4}"), "application/x-ipynb+json", "" },
				{ "crate.jsonld", text("{\"@context\": {}}"), "application/ld+json", "" },
				{ "config.yaml", text("a: 1\nb: [2, 3]\n"), "application/yaml", "" },
				{ "data.xml", text("<?xml version=\"1.0\"?><data/>"), "application/xml", "" },
				// Archives
				{ "archive.zip", at(64, 0, bytes('P', 'K', 3, 4)), "application/zip", "x-fmt/263" },
				{ "archive.tar", at(1024, 257, text("ustar")), "application/x-tar", "x-fmt/265" },
				{ "archive.bz2", at(64, 0, text("BZh9")), "application/x-bzip2", "x-fmt/268" },
				{ "archive.xz", at(64, 0, bytes(0xfd, '7', 'z', 'X', 'Z', 0)), "application/x-xz", "" },
				{ "archive.zst", at(64, 0, bytes(0x28, 0xb5, 0x2f, 0xfd)), "application/zstd", "" },
				{ "archive.7z", at(64, 0, bytes('7', 'z', 0xbc, 0xaf, 0x27, 0x1c)), "application/x-7z-compressed", "" },
				// Not known by content, nor guessed by name
				{ "array.npy", at(128, 0, text("\u0093NUMPY")), FileFormats.OCTET_STREAM, "" },
				{ "records.avro", at(64, 0, bytes('O', 'b', 'j', 1)), FileFormats.OCTET_STREAM, "" },
				{ "brain.nii", at(400, 344, bytes('n', '+', '1', 0)), FileFormats.OCTET_STREAM, "" },
		};
		List<String> wrong = new java.util.ArrayList<>();
		for (Object[] c : cases) {
			byte[] content = (byte[]) c[1];
			if (c[0].equals("array.npy")) {
				content = at(128, 0, bytes(0x93, 'N', 'U', 'M', 'P', 'Y'));
			}
			FileFormats.Detected detected = FileFormats.bundled().detect(write((String) c[0], content));
			String puid = detected.pronom().map(PRONOMFormats.Format::puid).orElse("");
			if (!detected.mediaType().equals(c[2]) || !puid.equals(c[3])) {
				wrong.add(c[0] + ": " + detected.mediaType() + " " + puid + ", not " + c[2] + " " + c[3]);
			}
		}
		assertEquals(List.of(), wrong);
	}
}
