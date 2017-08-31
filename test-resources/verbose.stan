parameters {
  real x;
}
model {
  print("sample: ", x);
  x ~ normal(0,1);
}
