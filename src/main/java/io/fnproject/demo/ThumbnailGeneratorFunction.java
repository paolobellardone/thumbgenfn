/*
 *
 * MIT License
 *
 * Copyright (c) 2022 PaoloB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package io.fnproject.demo;

import com.oracle.bmc.Region;

import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;

import com.oracle.bmc.objectstorage.model.CopyObjectDetails;
import com.oracle.bmc.objectstorage.model.WorkRequest;

import com.oracle.bmc.objectstorage.requests.CopyObjectRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.requests.GetWorkRequestRequest;

import com.oracle.bmc.objectstorage.responses.CopyObjectResponse;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse;
import com.oracle.bmc.objectstorage.responses.GetWorkRequestResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;

import javax.imageio.ImageIO;

/**
 * Main class that implements the thumbnail generation function.
 *
 * @version 1.0 15 Jun 2022
 * @author PaoloB
 */
public class ThumbnailGeneratorFunction {

    private ObjectStorage objStoreClient = null;
    private Boolean debug = Boolean.valueOf(getEnvVar("DEBUG", "false")); // If true, the logging will be more verbose

    final ResourcePrincipalAuthenticationDetailsProvider provider = ResourcePrincipalAuthenticationDetailsProvider.builder().build();
    final static String[] imageFormats = {"bmp", "gif", "jpeg", "jpg", "png", "tif", "tiff", "wbmp"};

    /**
     * Default constructor
     */
    public ThumbnailGeneratorFunction() {

        if (debug) {
            System.err.println("OCI_RESOURCE_PRINCIPAL_VERSION " + System.getenv("OCI_RESOURCE_PRINCIPAL_VERSION"));
            System.err.println("OCI_RESOURCE_PRINCIPAL_REGION " + System.getenv("OCI_RESOURCE_PRINCIPAL_REGION"));
            System.err.println("OCI_RESOURCE_PRINCIPAL_RPST " + System.getenv("OCI_RESOURCE_PRINCIPAL_RPST"));
            System.err.println("OCI_RESOURCE_PRINCIPAL_PRIVATE_PEM " + System.getenv("OCI_RESOURCE_PRINCIPAL_PRIVATE_PEM"));
        }

        try {
            objStoreClient = new ObjectStorageClient(provider);
        } catch (Throwable ex) {
            System.err.println("Failed to instantiate ObjectStorage client - " + ex.getMessage());
        }

    }

    /**
     * Scale the image with standard javax ImageIO.
     *
     * @param originalImage image to be converted
     * @param scaleWidth scaling ration for width (between 0 and 1)
     * @param scaleHeight scaling ratio for height (between 0 and 1)
     * @return the created thumbnail
     */
    private BufferedImage scaleImage(BufferedImage originalImage, double scaleWidth, double scaleHeight) {

        // Evaluate scaled width and height for thumbnail
        int targetWidth = (int) Math.ceil(originalImage.getWidth() * scaleWidth);
        int targetHeight = (int) Math.ceil(originalImage.getHeight() * scaleHeight);

        // Scale the image
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        graphics2D.scale(scaleWidth, scaleHeight);
        graphics2D.dispose();

        return resizedImage;

    }

    /**
     * Get the value of an environment variable or the default value if the variable is not defined
     *
     * @param variable name of the environment variable
     * @param defaultValue default value to be used if the variable is undefined
     * @return the value or the default value of the variable
     */
    private String getEnvVar(String variable, String defaultValue) {

        String value = System.getenv(variable);
        if (value == null)
            return defaultValue;
        else
            return value;

    }

    /**
     * Thumbnail generation function. It reads an image from a bucket defined in OCI Object Storage and then it creates a thumbnail
     * with a scaled size defined via environment variables. The result is copied to another bucket along with the original image.
     *
     * @return a message with the result of the operation invoked
     */
    public String handleRequest() {

        // Check if the OCI client is available, if not it exits with an error
        if (objStoreClient == null) {
            System.err.println("There was a problem creating the ObjectStorage Client object. Please check logs.");
            return "Error generating thumbnail, please check logs.";
        }

        // Reads the enviroment variables used to configure the OCI client and the image conversion engine
        String region = getEnvVar("OCI_REGION", "");
        String nameSpace = getEnvVar("OCI_NAMESPACE", "");
        String bucketIn = getEnvVar("BUCKET_IN", "imageIn");
        String bucketOut = getEnvVar("BUCKET_OUT", "ImageOut");
        // If the OCI-related parameters are not defined the function cannot proceed
        if ( region.isEmpty() || nameSpace.isEmpty() || bucketIn.isEmpty() || bucketOut.isEmpty() ) {
            System.err.println("The required environment variables OCI_REGION, OCI_NAMESPACE, BUCKET_IN, BUCKET_OUT are not defined. Please configure them before proceeding.");
            return "Error generating thumbnail, please check logs.";
        }
        String namePrefix = getEnvVar("NAME_PREFIX", "scaled-"); // The default namePrefix is "scaled-"
        double scalingFactor = Double.parseDouble(getEnvVar("SCALING_FACTOR", "0.5")); // The default scalingFactor is 0.5 (50% of the original size)
        String imageFormat = getEnvVar(("IMAGE_FORMAT").toLowerCase(), "jpg"); // imageFormat is one the following: BMP, GIF, JPEG, JPG, PNG, TIF, TIFF, WBMP, bmp, gif, jpeg, jpg, png, tif, tiff, wbmp. The default value is jpg
        // Check if the thumbnail extension is supported, if not it exits with an error
        if ( !Arrays.asList(imageFormats).contains(imageFormat) ) {
            System.err.println("The format " + imageFormat + " specified for output images is not supported, please choose one among: bmp, gif, jpeg, jpg, png, tif, tiff, wbmp");
            return "Error generating thumbnail, please check logs.";
        }

        List<String> objNames = null;
        String objectName = null;
        try {
            ListObjectsRequest lor = ListObjectsRequest.builder()
                                        .namespaceName(nameSpace)
                                        .bucketName(bucketIn)
                                        .build();

            ListObjectsResponse response = objStoreClient.listObjects(lor);

            objNames = response.getListObjects().getObjects().stream()
                            .map((objSummary) -> objSummary.getName())
                            .collect(Collectors.toList());

            objectName = objNames.stream()
                            .findFirst()
                            .map(Object::toString)
                            .orElse(null);

            // Read file from bucketIn
            GetObjectResponse getResponse = objStoreClient.getObject(
                                                GetObjectRequest.builder()
                                                    .namespaceName(nameSpace)
                                                    .bucketName(bucketIn)
                                                    .objectName(objectName)
                                                    .build()
                                            );

            System.err.println("Processing file: " + objectName);

            // Generate the thumbnail
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            BufferedImage originalImage = ImageIO.read(getResponse.getInputStream());
            BufferedImage outputImage = scaleImage(originalImage, scalingFactor, scalingFactor);
            ImageIO.write(outputImage, imageFormat, os);

            System.err.println("Finished processing file: " + objectName);

            // Put file to bucketOut
            ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
            PutObjectResponse putResponse = objStoreClient.putObject(
                                                PutObjectRequest.builder()
                                                    .namespaceName(nameSpace)
                                                    .bucketName(bucketOut)
                                                    .objectName(namePrefix + objectName)
                                                    .putObjectBody(is)
                                                    .build()
                                            );

            is.close();
            os.close();

            System.err.println("Created file: " + namePrefix + objectName);

            // To use the CopyObject APIs you need to allow Object Storage to access the tenancy
            CopyObjectResponse copyOriginalImage = objStoreClient.copyObject(
                                                        CopyObjectRequest.builder()
                                                            .namespaceName(nameSpace)
                                                            .bucketName(bucketIn)
                                                            .copyObjectDetails(
                                                                CopyObjectDetails.builder()
                                                                    .sourceObjectName(objectName)
                                                                    .destinationRegion(region)
                                                                    .destinationNamespace(nameSpace)
                                                                    .destinationBucket(bucketOut)
                                                                    .destinationObjectName(objectName)
                                                                    .build()
                                                            )
                                                            .build()
                                                    );

            GetWorkRequestResponse getWorkRequestResponse = objStoreClient.getWaiters().forWorkRequest(
                                                                GetWorkRequestRequest.builder()
                                                                    .workRequestId(copyOriginalImage.getOpcWorkRequestId())
                                                                    .build()
                                                                )
                                                                .execute();
            WorkRequest.Status status = getWorkRequestResponse.getWorkRequest().getStatus();

            if (status == WorkRequest.Status.Completed) {
                System.err.println("Copied original file to destination: " + objectName);
                // Delete the source object only after the successful copy of the file
                DeleteObjectResponse deleteProcessedImage = objStoreClient.deleteObject(
                    DeleteObjectRequest.builder()
                        .namespaceName(nameSpace)
                        .bucketName(bucketIn)
                        .objectName(objectName)
                        .build()
                );
            }

            objStoreClient.close();

            System.err.println("Thumbnail generation completed, please see the output in bucket " + bucketOut);
            return "Thumbnail generation completed, please see the output in bucket " + bucketOut;

        } catch (Throwable e) {
            System.err.println("Error during thumbnail generation: " + e.getMessage());
            return "Error during thumbnail generation, please check logs.";
        }

    }

}
