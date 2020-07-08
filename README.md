# Distributed Systems Project
Distributed Election system for the USA

## Technologies
ZooKeeper, Docker Containers, gRPC, protobuf

## Implementation Summary
- Each state is represented by a shard of nodes
- Each node is a valid elections server, which can receive vote requests from voters
- We used Zookeeper to implement the following: leader election, group membership, failure detector and atomic broadcast
- We used Docker for running the replicated zooKeeper servers, election servers, clients and a committee client which is responsible for the Election results

## Design Overview
![alt text](https://github.com/ronYitzhak/DistributedSystemsProject/blob/master/DesignDepiction.png?raw=true)

## Distributes Systems services implementation using ZooKeeper
### Group Membership
The Election/\<state\>/LiveNodes namespace used as a view of group membership. Each live node is represented by a zNode which contains its identity, under the state's LiveNodes namespaces.
### Failure Detector
Each node creates an ephemeral sequential zNode to represent its liveness in the system. Once a node is no longer available, the connection to its zNode will be lost. As a result, the zNode will be destroyed and the node will be no longer part of the system. An event is triggered once a zNode is destroyed to notify the system about the change.
### Leader Election
Each state has a leader which is the node that is represented by the smallest sequential zNode under Election/\<state\>/LiveNodes namespace. Servers' zNodes keeps the identity of the server as data, hence the identity can be retrieved easily.
- Once a leader is elected, all the live nodes in the shard start listening on the leader’s zNode.
- Once a leader fails, its zNode under /Election/\<state\>/LiveNodes is deleted. All the nodes in the shard get a notification that makes them choose a new leader.
### Atomic Broadcast
The leader node propagates a vote request to all the state's servers. It catches a lock until the vote request will be sent to all the live servers in the state's shard. Each server that receives the request, saves it locally without handling it. Each server has an Atomic Boolean used to indicate whether it has a pending request to handle. After the requests have been sent to all shard's servers successfully, the leader changes the Election/\<state\>/Commit zNode data to trigger an event in all the shard's servers. This event tells the servers they can safely commit their changes, and used to ensure the atomicity of the request



  
