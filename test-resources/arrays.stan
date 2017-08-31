data {
  int<lower=0> N;
  int<lower=0> K;

  cov_matrix[K] sample_cov[N];
}
parameters {
  cov_matrix[K] phi;
}
model {
  for (i in 1:N)
    sample_cov[i] ~ wishart(1000, phi);
}
