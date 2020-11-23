# Distributed Systems
Repository created to host projects developed in the Distributed Systems.

The Course focuses in Distributed Systems concepts using several Java libraries for implementation of those concepts.

## Labs 

This folder stores exercises solved to gain some practical insight in Distributed Systems concepts. They are divided in different labs, each one targeting a different concept.

- **Lab1:** Programming with Java Datagram Sockets
- **Lab2:** Programming distributed applications using IP Multicast
- **Lab3:** Programming distributed applications with Java RMI
- **Lab4:** Programming distributed applications using the Java API for TCP 

## Projects

Projects developed to apply Distributed Systems concepts in larger scale systems. Project1 was developed with my partner [João Campos](https://github.com/Pastilhas) and Project 2 was developed with him and also [João Pinto](https://github.com/joaorenatopinto) and [Afonso Mendonça](https://github.com/afonsoafonsoafonso).

- **Project1:** Distributed file backup service. It implements a custom protocol to split a file in chunks and store it in different peers with the desired redundancy degree. It also implements a client application to interact with the servers.
- **Project2:** A more advanced distributed file backup system. It uses [Chord](https://en.wikipedia.org/wiki/Chord_(peer-to-peer)) to find which peer stores which chunk. It also uses SSLEngine to encrypt messages that are sent using Asynchronous Socket Channels for maximum scalability. The system focus on maximum scalability and fault tolerance.

---

**Note**:If you find some mistake in this readme or any other part of this repository, feel free to tell me about it!
