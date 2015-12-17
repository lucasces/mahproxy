# MAHproxy
Sample Java HTTP/HTTPS proxy

## Build

```sh
$ mvn package install
```

## Test

- Dowload [apache karaf] and extract it.
- Run *bin/karaf* and type the following commands on karaf shell:

```sh
> install mvn:org.apache.commons/commons-lang3
> install mvn:commons-io/commons-io/2.4
> install mvn:com.github.lucasces/mahproxy
```

Set your browser's http/https proxy to *localhost:10000*

[apache karaf]: <http://karaf.apache.org/index/community/download.html>
