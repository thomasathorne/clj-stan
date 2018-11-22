# clj-stan

A Clojure library that interfaces with the STAN statistical modeling
platform as an external process.

## Setup

This project uses the `cmdstan` command line interface to STAN.

To install `cmdstan` version `2.18.0` (other versions should work too,
but may need different dependencies), make sure you have the necessary
dependencies installed:

    sudo apt-get install clang g++ libc++-dev

and then download the tar file `cmdstan-2.18.0.tar.gz` from
https://github.com/stan-dev/cmdstan/releases. Extract the archive, and
then run

    make build -j4

in the resulting directory. The `-j4` option parallelises the build,
which is advisable since it takes 10+ minutes and is quite CPU
intensive.

You must configure the environment variable `$STAN_HOME` to be the
path to the directory extracted from the release tar.

This process is scripted in the `install` directory. There is also a
`Dockerfile` there, which is intended to build a base image for
clojure apps that use `clj-stan`.

A simple way to check that things are correctly configured is to run
the (fairly minimal) test suite:

    me@machine:~/projects/clj-stan$ lein test

## Usage

Suppose we have the following model written in the file
`/models/bernoulli.stan`:

```
data {
  int<lower=0> N;
  int<lower=0,upper=1> y[N];
}
parameters {
  real<lower=0,upper=1> theta;
}
model {
  theta ~ beta(0.5,0.5);
  for (n in 1:N)
    y[n] ~ bernoulli(theta);
}
```

which expresses a simple bernoulli trial model with the Jeffreys
prior.

There are three core functions we will use:

    (def bern (stan/make "/models/bernoulli.stan" "bern"))

will compile the model and return a record that wraps the resulting
executable. This record implements two methods:

    (stan/sample bern {:N 3 :y [0 1 1]})

the primary sampling method, returns a collection of samples from the
posterior distribution of the model, and:

    (stan/optimize bern {:N 3 :y [0 1 1]})

calls the optimization routine of the executable, which will find the
MAP ('Maximum A Posteriori') estimate for the model.

Alternatively, the variational bayes approach can be used for model
training

    (stan/variational bern {:N 3 :y [0 1 1]} "fullrank")

The third parameter specifies the variational algorithm to be used,
which can be either `meanfield` (using a fully factored Gaussian for
the approximation) or `fullrank` (using a Gaussian with full-rank
covariance matrix for the optimization). The `variational` method
returns a map containing keys `:mode` and `:samples`.

## Resources

See the cmdstan documentation at
http://mc-stan.org/users/interfaces/cmdstan

## License

```
  Copyright (c) Metail and Thomas Athorne

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
```
