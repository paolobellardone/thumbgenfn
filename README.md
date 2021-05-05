# Serverless Thumbnail Generator

A serverless application that reads an image and then renders a scaled down version using Oracle Functions, Object Storage and Events.
## Pre-requisites

First of all you need an OCI tenancy and a user member of a group having the right policies to use Functions, Object Storage and Events. Then you need to follow these steps:

1. Create two Object Storage buckets, one for input and one for output, for example *fninput* and *fnputput*
2. Enable the Emit Object Events setting for the input bucket
3. Create a repository in Oracle Registry to deploy the serverless function, for example [ocir-repo]
4. Create a rule in the Events service to listen to events emitted by Object Storage and to activate the function to scale the image down
5. Create a Functions application and configure logging
6. Configure the environment used to execute the function by specifying the variables needed to run the code.

## Configure and deploy the function

Clone this repository

```sh
git clone https://github.com/paolobellardone/thumbgenfn.git
```

<!--
Initialize your function

```sh
fn init --runtime java [function-name]
```
-->

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

## License

MIT License

Copyright (c) 2021 PaoloB

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
