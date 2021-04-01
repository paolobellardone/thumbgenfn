/*
 *
 * MIT License
 *
 * Copyright (c) 2021 PaoloB
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

import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.StringPrivateKeySupplier;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;

import com.oracle.bmc.objectstorage.model.CopyObjectDetails;

import com.oracle.bmc.objectstorage.requests.CopyObjectRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;

import com.oracle.bmc.objectstorage.responses.CopyObjectResponse;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.name.Rename;

public class ThumbnailGeneratorFunction {

    private ObjectStorage objStoreClient = null;

    // Read the private key from the environment and substitute the token |n| with \n to support multiline
    private String privateKeyPEM = System.getenv("OCI_PRIVATE_KEY").replace("|n|","\n");

    final SimpleAuthenticationDetailsProvider provider = SimpleAuthenticationDetailsProvider.builder()
                                                            .region(Region.fromRegionId(System.getenv("OCI_REGION"))) // Decode Region from Id
                                                            .tenantId(System.getenv("OCI_TENANT_ID"))
                                                            .userId(System.getenv("OCI_USER_ID"))
                                                            .privateKeySupplier(new StringPrivateKeySupplier(privateKeyPEM))
                                                            .fingerprint(System.getenv("OCI_FINGERPRINT"))
                                                            .passPhrase(System.getenv("OCI_PASSPHRASE"))
                                                            .build();

    public ThumbnailGeneratorFunction() {
        try {
            objStoreClient = new ObjectStorageClient(provider);
        } catch (Throwable ex) {
            System.err.println("Failed to instantiate ObjectStorage client - " + ex.getMessage());
        }
    }

    public String handleRequest() {
        if (objStoreClient == null) {
            System.err.println("There was a problem creating the ObjectStorage Client object. Please check logs.");
            return "Error generating thumbnail, please check logs";
        }

        // fetch the file from the object storage
        String nameSpace = System.getenv("OCI_NAMESPACE");
        String bucketIn = System.getenv("BUCKET_IN");
        String bucketOut = System.getenv("BUCKET_OUT");

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

            // Generate the thumbnail
            // TODO: parametrize the resolution and the output format
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Thumbnails.of(getResponse.getInputStream())
                      //.size(640, 480)
                      .scale(0.5) // the thumbnail is 50% smaller
                      .outputFormat("jpg")
                      .toOutputStream(os);
                      //.toFiles(new File("./output"), Rename.SUFFIX_HYPHEN_THUMBNAIL);

            // Put file to bucketOut
            ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
            PutObjectResponse putResponse = objStoreClient.putObject(
                                                PutObjectRequest.builder()
                                                    .namespaceName(nameSpace)
                                                    .bucketName(bucketOut)
                                                    .objectName("Scaled-" + objectName) // TODO: parametrize the name???
                                                    //.putObjectBody(getResponse.getInputStream())
                                                    .putObjectBody(is)
                                                    .build()
                                            );

            os.close();

            CopyObjectResponse copyOriginalImage = objStoreClient.copyObject(
                                                        CopyObjectRequest.builder()
                                                            .namespaceName(nameSpace)
                                                            .bucketName(bucketIn)
                                                            .copyObjectDetails(
                                                                CopyObjectDetails.builder()
                                                                    .sourceObjectName(objectName)
                                                                    .destinationRegion(System.getenv("OCI_REGION"))
                                                                    .destinationNamespace(nameSpace)
                                                                    .destinationBucket(bucketOut)
                                                                    .destinationObjectName(objectName)
                                                                    .build()
                                                            )
                                                            .build()
                                                    );
            DeleteObjectResponse deleteProcessedImage = objStoreClient.deleteObject(
                                                            DeleteObjectRequest.builder()
                                                                .namespaceName(nameSpace)
                                                                .bucketName(bucketIn)
                                                                .objectName(objectName)
                                                                .build()
                                                        );

            objStoreClient.close();

        } catch (Throwable e) {
            System.err.println("Error fetching object list from bucket " + e.getMessage());
        }

        return "Thumbnail generation completed, please see the output in bucket " + bucketOut;
    }

}