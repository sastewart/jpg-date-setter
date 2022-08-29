package com.sastewa.jpgdatesetter;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandLine.Command(
        name = "Jpg Date Setter",
        description = "Sets the date-taken on a directory of jpg files",
        version = "1.0")
public class JpgDateSetter implements Callable<String> {

    public static final DateTimeFormatter JPG_DATE_FORMATTER = DateTimeFormatter.ofPattern("YYYY:MM:dd HH:mm:ss");

    @CommandLine.Option(names = {"-p", "--pattern"}, description = "Glob pattern used to select image files. " +
            "EX: *.jpg")
    String globPattern = "*";

    @CommandLine.Option(names = {"-d", "--debug"},
            description = "Only read the files, do not write them with the updated date")
    boolean debug = false;

    @CommandLine.Parameters(paramLabel = "<from dir>",
            description = "The directory that contains the jpg files")
    private Path fromDir;

    @CommandLine.Parameters(paramLabel = "<to dir>",
            description = "The directory to write the jpg files to with the new date")
    private File toDir;

    @CommandLine.Parameters(paramLabel = "<start date/time>",
            description = "The date time to start from in ISO 8601 format. EX: 2021-01-20T17:00:01Z or for EST " +
                    "2021-01-20T17:00:01-05:00")
    private String isoStartDate;

    @CommandLine.Parameters(paramLabel = "<increment by>",
            description = "An ISO 8601 duration to increment the date on each successive jpg file. EX: PT10M " +
                    "(to increment by 10 minutes)")
    private String isoDuration;

    public JpgDateSetter() {
    }

    public static void main(String[] args) throws IOException, ImageReadException, ImageWriteException {
        int exitCode = new CommandLine(new JpgDateSetter()).execute(args);
        System.exit(exitCode);
    }

    private static void printTagValue(final JpegImageMetadata jpegMetadata,
                                      final TagInfo tagInfo) {
        final TiffField field = jpegMetadata.findEXIFValueWithExactMatch(tagInfo);
        if (field == null) {
            System.out.println(tagInfo.name + ": " + "Not Found.");
        } else {
            System.out.println(tagInfo.name + ": "
                    + field.getValueDescription());
        }
    }

    public String call() throws IOException, ImageReadException, ImageWriteException {
        System.out.format("Parameters - FROM: %s, TO: %s, START-DATE: %s, BY: %s, pattern: %s\n",
                fromDir, toDir, isoStartDate, isoDuration, globPattern);

        // setup the time stuff
        Clock clockStart = Clock.fixed(Instant.parse(isoStartDate), ZoneId.of(ZoneId.SHORT_IDS.get("EST")));
        LocalDateTime ldt = LocalDateTime.now(clockStart);
        Duration duration = Duration.parse(isoDuration);

        // setup the file and directory stuff
        if (!toDir.exists() && !toDir.mkdirs()) {
            System.out.println("Failed to make directory: " + toDir);
            System.exit(1);
        }
        String pat = "glob:" + fromDir + File.separator + globPattern;
        PathMatcher pm = FileSystems.getDefault().getPathMatcher(pat);
        List<Path> images;
        try (Stream<Path> walk = Files.walk(fromDir)) {
            images = walk
                    .filter(Files::isRegularFile)
                    .filter(f -> pm.matches(f))
                    // not sure why the takeWhile does not work
//                    .takeWhile(Files::isRegularFile)
//                    .takeWhile(f -> pm.matches(f))
                    .sorted()
                    .collect(Collectors.toList());
        }

        // process each file
        for (Path srcImgFile : images) {
            System.out.println("SOURCE: " + srcImgFile);
            final ImageMetadata imd = Imaging.getMetadata(srcImgFile.toFile());
            if (!(imd instanceof JpegImageMetadata)) {
                System.out.println("File: %s is not a jpg image");
                continue;
            }
            final JpegImageMetadata jpegMetadata = (JpegImageMetadata) imd;
            printTagValue(jpegMetadata, TiffTagConstants.TIFF_TAG_DATE_TIME);
            printTagValue(jpegMetadata, ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
            printTagValue(jpegMetadata, ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED);
            String dstImgFile = toDir + File.separator + srcImgFile.getFileName();

            if (debug) continue;

            // setup an output stream to the destination file
            try (FileOutputStream fos = new FileOutputStream(dstImgFile);
                 OutputStream destOs = new BufferedOutputStream(fos)) {
                System.out.println("DEST: " + dstImgFile);

                // add increment date
                String jpgDate = ldt.format(JPG_DATE_FORMATTER);
                TiffOutputSet outputSet = null;

                // note that metadata might be null if no metadata is found.
                if (null != jpegMetadata) {
                    // note that exif might be null if no Exif metadata is found.
                    final TiffImageMetadata exif = jpegMetadata.getExif();

                    if (null != exif) {
                        // TiffImageMetadata class is immutable (read-only). TiffOutputSet class represents
                        // the Exif data to write.
                        //
                        // Usually, we want to update existing Exif metadata by changing the values of a few fields,
                        // or adding a field. In these cases, it is easiest to use getOutputSet() to start with a
                        // "copy" of the fields read from the image.
                        outputSet = exif.getOutputSet();
                    }
                }

                // if file does not contain any exif metadata, we create an empty
                // set of exif metadata. Otherwise, we keep all of the other existing tags.
                if (null == outputSet) {
                    outputSet = new TiffOutputSet();
                }

                // Note that you should first remove the field/tag if it already exists in this directory,
                // or you may end up with duplicate tags.
                //
                // Certain fields/tags are expected in certain Exif directories; Others can occur in more than one
                // directory (and often have a different meaning in different directories).
                //
                // TagInfo constants often contain a description of what directories are associated with a given tag.
                //
                final TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
                // make sure to remove old value if present (this method will
                // not fail if the tag does not exist).
                exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
                exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, jpgDate);
                exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED);
                exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, jpgDate);

                // update the date-time for the next image
                ldt = ldt.plus(duration);

                new ExifRewriter().updateExifMetadataLossless(srcImgFile.toFile(), destOs,
                        outputSet);

                JpegImageMetadata dstMetadata = (JpegImageMetadata) Imaging.getMetadata(Path.of(dstImgFile).toFile());
                printTagValue(dstMetadata, TiffTagConstants.TIFF_TAG_DATE_TIME);
                printTagValue(dstMetadata, ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
                printTagValue(dstMetadata, ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED);
            }

        }

        return "";
    }
}
