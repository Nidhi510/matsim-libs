# DRT

This extension provides the demand-responsive transport service functionality in MATSim.
It uses the DVRP contrib to implement DRT as a on-demand transport service, i.e. DRT requests are submitted in an online manner. 
The main difference to the taxi contrib is that rides can be shared (e.g. shared taxis or minibuses) and occupied vehicles can be diverted from the current destination to pick up new passengers on the way (taxi allowed diversion for empty vehicles only).   