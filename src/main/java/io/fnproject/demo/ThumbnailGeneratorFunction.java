/*
 *
 * MIT License
 *
 * Copyright (c) 2022-23 PaoloB
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

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fnproject.fn.api.FnConfiguration;
import com.fnproject.fn.api.RuntimeContext;

import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.CopyObjectDetails;
import com.oracle.bmc.objectstorage.model.WorkRequest;
import com.oracle.bmc.objectstorage.requests.CopyObjectRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetWorkRequestRequest;
import com.oracle.bmc.objectstorage.responses.CopyObjectResponse;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetWorkRequestResponse;

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class that implements the thumbnail generation function.
 *
 * @version 1.6 12 Apr 2023
 * @author PaoloB
 */
public class ThumbnailGeneratorFunction {

    // Variables to save the environment variables of the function
    private Boolean debug;        // DEBUG - Enables debugging informations in log files
    private String region;        // OCI_REGION - OCI region
    private String nameSpace;     // OCI_NAMESPACE - Object Storage namespace
    private String bucketIn;      // BUCKET_IN - Bucket that emits events when an image is uploaded
    private String bucketOut;     // BUCKET_OUT - Bucket used to store the generated thumbnail and the original image
    private String namePrefix;    // NAME_PREFIX - Prefix for the name of the generated thumbnail
    private double scalingFactor; // SCALING_FACTOR - Factor to scale the image, it should be < 1 to scale down
    private String imageFormat;   // IMAGE_FORMAT - Format of the generated thumbnail

    // Variables to save the internal environment variables
    private String ociResourcePrincipalVersion; // OCI_RESOURCE_PRINCIPAL_VERSION
    private String ociResourcePrincipalRegion;  // OCI_RESOURCE_PRINCIPAL_REGION
    private String ociResourcePrincipalRPST;    // OCI_RESOURCE_PRINCIPAL_RPST
    private String ociResourcePrincipalPEM;     // OCI_RESOURCE_PRINCIPAL_PRIVATE_PEM

    // Authentication using Resource Principal
    final ResourcePrincipalAuthenticationDetailsProvider provider = ResourcePrincipalAuthenticationDetailsProvider.builder().build();

    // Supported image formats
    static final String[] imageFormats = { "bmp", "gif", "jpeg", "jpg", "png", "tif", "tiff", "wbmp" };

    // Generic error message
    static final String ERRORMSG = "Error generating thumbnail, please check logs.";

    /**
     * Get the configuration fron the environment configured for the function
     *
     * @param ctx the runtime context of the function
     */
    @FnConfiguration
    public void config(RuntimeContext ctx) {

        // If true, the logging will be more verbose
        debug = Boolean.valueOf(ctx.getConfigurationByKey("DEBUG").orElse("false"));
        region = ctx.getConfigurationByKey("OCI_REGION").orElseThrow(() -> new RuntimeException("Missing configuration: OCI_REGION"));
        nameSpace = ctx.getConfigurationByKey("OCI_NAMESPACE").orElseThrow(() -> new RuntimeException("Missing configuration: OCI_NAMESPACE"));
        // Default value for BUCKET_IN is imageIn
        bucketIn = ctx.getConfigurationByKey("BUCKET_IN").orElse("imageIn");
        // Default value for BUCKET_OUT is imageOut
        bucketOut = ctx.getConfigurationByKey("BUCKET_OUT").orElse("ImageOut");
        // The default namePrefix is "scaled-"
        namePrefix = ctx.getConfigurationByKey("NAME_PREFIX").orElse("scaled-");
        // The default scalingFactor is 0.5 (50% of the original size)
        scalingFactor = Double.parseDouble(ctx.getConfigurationByKey("SCALING_FACTOR").orElse("0.5"));
        // imageFormat is one the following: BMP, GIF, JPEG, JPG, PNG, TIF, TIFF, WBMP, bmp, gif, jpeg, jpg, png, tif, tiff, wbmp.
        // The default value is jpg
        imageFormat = ctx.getConfigurationByKey("IMAGE_FORMAT").orElse("jpg");
        imageFormat = imageFormat.toLowerCase();

        // OCI_RESOURCE_PRINCIPAL_VERSION
        ociResourcePrincipalVersion = ctx.getConfigurationByKey("OCI_RESOURCE_PRINCIPAL_VERSION").orElseThrow(() -> new RuntimeException("Missing configuration: OCI_RESOURCE_PRINCIPAL_VERSION"));
        // OCI_RESOURCE_PRINCIPAL_REGION
        ociResourcePrincipalRegion = ctx.getConfigurationByKey("OCI_RESOURCE_PRINCIPAL_REGION").orElseThrow(() -> new RuntimeException("Missing configuration: OCI_RESOURCE_PRINCIPAL_REGION"));
        // OCI_RESOURCE_PRINCIPAL_RPST
        ociResourcePrincipalRPST = ctx.getConfigurationByKey("OCI_RESOURCE_PRINCIPAL_RPST").orElseThrow(() -> new RuntimeException("Missing configuration: OCI_RESOURCE_PRINCIPAL_RPST"));
        // OCI_RESOURCE_PRINCIPAL_PRIVATE_PEM
        ociResourcePrincipalPEM = ctx.getConfigurationByKey("OCI_RESOURCE_PRINCIPAL_PRIVATE_PEM").orElseThrow(() -> new RuntimeException("Missing configuration: OCI_RESOURCE_PRINCIPAL_PRIVATE_PEM"));

    }

    /**
     * Scale the image with standard javax ImageIO.
     *
     * @param originalImage image to be converted
     * @param scaleWidth    scaling ration for width (between 0 and 1)
     * @param scaleHeight   scaling ratio for height (between 0 and 1)
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
     * Thumbnail generation function. It reads an image from a bucket defined in OCI
     * Object Storage and then it creates a thumbnail
     * with a scaled size defined via environment variables. The result is copied to
     * another bucket along with the original image.
     *
     * @param ctx          Oracle Functions runtime context
     * @param eventPayload Oracle Events payload passed to function
     * @return a message with the result of the operation invoked
     */
    public String handleRequest(RuntimeContext ctx, String eventPayload) {

        // Create a logger instance to print messages to System.err
        Logger logger = LoggerFactory.getLogger(ThumbnailGeneratorFunction.class);

        // Print out some configuration details for debugging purposes
        if (Boolean.TRUE.equals(debug)) {
            logger.info("OCI_RESOURCE_PRINCIPAL_VERSION: {}", ociResourcePrincipalVersion);
            logger.info("OCI_RESOURCE_PRINCIPAL_REGION: {}", ociResourcePrincipalRegion);
            logger.info("OCI_RESOURCE_PRINCIPAL_RPST: {}", ociResourcePrincipalRPST);
            logger.info("OCI_RESOURCE_PRINCIPAL_PRIVATE_PEM: {}", ociResourcePrincipalPEM);
        }

        // Create a client to access the Object Storage service
        ObjectStorage objStorageClient = null;
        objStorageClient = ObjectStorageClient.builder().build(provider);

        // Check if the OCI client is available, if not it exits with an error
        if (objStorageClient == null) {
            logger.error("There was a problem creating the ObjectStorageClient object. Please check logs.");
            return ERRORMSG;
        }

        // If the OCI-related parameters are not defined the function cannot proceed
        if (region.isEmpty() || nameSpace.isEmpty() || bucketIn.isEmpty() || bucketOut.isEmpty()) {
            logger.error("The required environment variables OCI_REGION, OCI_NAMESPACE, BUCKET_IN, BUCKET_OUT are not defined. Please configure them before proceeding.");
            return ERRORMSG;
        }

        // Check if the thumbnail extension is supported, if not it exits with an error
        if (!Arrays.asList(imageFormats).contains(imageFormat)) {
            logger.error("The format {} specified for output images is not supported, please choose one among: bmp, gif, jpeg, jpg, png, tif, tiff, wbmp", imageFormat);
            return ERRORMSG;
        }

        // Generate the thumbnail for the image uploaded into input bucket
        try {

            ObjectMapper mapper = new ObjectMapper();
            ObjectStorageCloudEvent osCloudEvent = mapper.readValue(eventPayload, ObjectStorageCloudEvent.class);
            String fileName = osCloudEvent.getData().get("resourceName").toString();

            logger.info("Processing file: {}", fileName);

            // Read file from bucketIn
            GetObjectResponse getObjectResponse = objStorageClient.getObject(GetObjectRequest.builder()
                                                                                .namespaceName(nameSpace)
                                                                                .bucketName(bucketIn)
                                                                                .objectName(fileName)
                                                                                .build());
            // Generate the thumbnail
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            BufferedImage originalImage = ImageIO.read(getObjectResponse.getInputStream());
            BufferedImage outputImage = scaleImage(originalImage, scalingFactor, scalingFactor);
            ImageIO.write(outputImage, imageFormat, os);

            // Put file to bucketOut
            ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
            PutObjectResponse putObjectResponse = objStorageClient.putObject(PutObjectRequest.builder()
                                                                                .namespaceName(nameSpace)
                                                                                .bucketName(bucketOut)
                                                                                .objectName(namePrefix + fileName)
                                                                                .putObjectBody(is)
                                                                                .build());

            if (putObjectResponse == null) {
                logger.error("Error creating thumbnail file: {}{}", namePrefix, fileName);
                return ERRORMSG;
            } else {
                logger.info("Created thumbnail file: {}{}", namePrefix, fileName);
            }

            logger.info("Finished processing file: {}", fileName);

            is.close();
            os.close();

            // To use the CopyObject APIs you need to allow Object Storage to access the tenancy
            // Copy the original image along with the thumbnail in bucketOut
            CopyObjectResponse copyOriginalImage = objStorageClient.copyObject(CopyObjectRequest.builder()
                                                                                .namespaceName(nameSpace)
                                                                                .bucketName(bucketIn)
                                                                                .copyObjectDetails(CopyObjectDetails.builder()
                                                                                                    .sourceObjectName(fileName)
                                                                                                    .destinationRegion(region)
                                                                                                    .destinationNamespace(nameSpace)
                                                                                                    .destinationBucket(bucketOut)
                                                                                                    .destinationObjectName(fileName)
                                                                                                    .build())
                                                                                .build());

            // The call is asynchronous, wait until it is finished
            GetWorkRequestResponse getWorkRequestResponse = objStorageClient.getWaiters().forWorkRequest(
                                                                                            GetWorkRequestRequest.builder()
                                                                                                .workRequestId(copyOriginalImage.getOpcWorkRequestId())
                                                                                            .build())
                                                                                          .execute();
            WorkRequest.Status workRequestStatus = getWorkRequestResponse.getWorkRequest().getStatus();

            if (workRequestStatus == WorkRequest.Status.Completed) {
                logger.info("Copied original file to destination: {}", fileName);

                // Delete the source object only after the successful copy of the file
                final DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                                                                    .namespaceName(nameSpace)
                                                                    .bucketName(bucketIn)
                                                                    .objectName(fileName)
                                                                    .build();
                DeleteObjectResponse deleteProcessedImage = objStorageClient.deleteObject(deleteObjectRequest);
                if (deleteProcessedImage == null) {
                    logger.error("Error deleting file: {}", fileName);
                    return ERRORMSG;
                } else {
                    logger.info("Deleted file: {}", fileName);
                }
            }

            objStorageClient.close();

            logger.info("Thumbnail generation completed, please see the output in bucket {}", bucketOut);
            return "Thumbnail generation completed, please see the output in bucket " + bucketOut;

        } catch (Exception e) {
            logger.error("Error during thumbnail generation: {}", e.getMessage());
            return ERRORMSG;
        }

    }

}
