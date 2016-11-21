KTH H16P02 - DAIAIA - HW2 - readme

Group 25
Fannar Magnusson (fannar@kth.se)
Thorsteinn Thorri Sigurdsson (ttsi@kth.se)

Build the program and create the .jar file:
Java 8 and Gradle 3.0 is required to build the program.
In command line: navigate to the project directory ("HW2"), there you should see the file "build.gradle". 
Run the command line function "gradle build". Build should be successful and the .jar file is now available at "HW2/build/libs".

Run the .jar file:
java -jar build/libs/kth-h16p02-daiaia-hw2.jar -agents "artistManagerAgent:agents.ArtistManagerAgent;curatorAgent1:agents.CuratorAgent(aggressive);curatorAgent2:agents.CuratorAgent(passive);curatorAgent3:agents.CuratorAgent(passive);curatorAgent4:agents.CuratorAgent(passive)"