# Serverless Thumbnail Generator

A serverless application that reads an image and then renders a scaled version using Oracle Functions, Object Storage, Events, and Logging.

## Pre-requisites

First of all you need an OCI tenancy and a user member of a group having the right policies to use Functions, Object Storage and Events. You need also to authorize Object Storage service to manage your object and buckets in order to use the CopyObject APIs. Then you need to follow these steps:

1. Create a dynamic group to give functions in a specific compartment acceess to object storage

    > ALL {resource.type = 'fnfunc', resource.compartment.id = 'ocid1.compartment.oc1.. ...'}

2. Create policies
    1. Policies to allow Functions Cloud Service to interact with other OCI services such as Oracle Repository and Key Vault

        >allow service faas to use apm-domains in tenancy

        >allow service faas to read repos in tenancy where request.operation='ListContainerImageSignatures'

        >allow service faas to {KEY_READ} in tenancy where request.operation='GetKeyVersion'

        >allow service faas to {KEY_VERIFY} in tenancy where request.operation='Verify'

    2. Policies to allow functions of a compartment to read and write objects into specific buckets

        >allow dynamic-group fn-all-cloudnative to inspect buckets in compartment CloudNative

        >allow dynamic-group fn-all-cloudnative to manage objects in compartment CloudNative where any {target.bucket.name='imageIn', target.bucket.name='imageOut'}

    3. Policy to allow Object Storage to manage objects in tenancy

        >allow service objectstorage-[region] to manage object-family in tenancy

3. Create two Object Storage buckets, one for input and one for output, for example *imageIn* and *imageOut*. *Please note that they should match with those specified in policy 2.2 above*.
4. Enable the **Emit Object Events** setting for the input bucket
5. Create a repository in Oracle Registry to deploy the serverless function
6. Create a Functions application using OCI console (see OCI docs if needed) and configure Logging (see OCI docs if needed)
    1. See [Configure and deploy the function](#configure-and-deploy-the-function)
7. Configure the environment used to execute the function by specifying the variables needed to run the code
    1. See [Environment configuration](#environment-configuration)
8. Create a rule in the Events service to listen to events emitted by Object Storage and to activate the function to scale down the image
    1. Event type: *Object - Create*
    2. Attributes: *bucketName = imageIn* (this is the name specified in 3 and 2.2)

## Configure and deploy the function

Clone this repository

```sh
git clone https://github.com/paolobellardone/thumbgenfn.git
```

Switch into the generated directory

```sh
cd [function-name]
```

Create a context for this compartment and select it for use

```sh
fn create context [compartment-name] --provider oracle
fn use context [compartment-name]
```

Update the context with the compartment ID and the Oracle Functions API URL

```sh
fn update context [compartment-id]
fn update context api-url https://functions.[region-id].oraclecloud.com
```

Update the context with the location of the Registry you want to use

```sh
fn update context registry [region-key].ocir.io/[tenancy-namespace]/[ocir-repo]
```

Log into the Registry using the Auth Token as your password

```sh
docker login -u '[tenancy-namespace]/[username]' [region-key].ocir.io
```

Deploy your function

```sh
fn deploy --app [application-name]
```

Invoke your function

```sh
fn invoke [application-name] [function-name]
```

## Environment configuration

The environment variables needed to pass the parameters to the function can be specified at the application level or at function level.

| Variable       | Default value |
|----------------|---------------|
| OCI_NAMESPACE  | -             |
| OCI_REGION     | -             |
| BUCKET_IN      | imageIn       |
| BUCKET_OUT     | imageOut      |
| NAME_PREFIX    | scaled-       |
| IMAGE_FORMAT   | jpg           |
| SCALING_FACTOR | 0.5           |
| DEBUG          | false         |

## License

MIT License

Copyright (c) 2022-24 PaoloB

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
