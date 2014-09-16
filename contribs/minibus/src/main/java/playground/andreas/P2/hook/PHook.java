/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.andreas.P2.hook;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.ScoringEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.controler.listener.ScoringListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.router.PlanRouter;
import org.matsim.core.router.TripRouterFactory;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.population.algorithms.AbstractPersonAlgorithm;
import org.matsim.population.algorithms.ParallelPersonAlgorithmRunner;
import org.matsim.pt.transitSchedule.TransitScheduleWriterV1;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleWriterV1;
import org.matsim.vehicles.Vehicles;
import playground.andreas.P2.helper.PConfigGroup;
import playground.andreas.P2.pbox.PBox;
import playground.andreas.P2.stats.StatsManager;
import playground.andreas.P2.stats.abtractPAnalysisModules.lineSetter.PtMode2LineSetter;

import java.util.HashSet;
import java.util.Set;

/**
 * Hook to register paratransit black box with MATSim
 * 
 * @author aneumann
 */
public final class PHook implements IterationStartsListener, StartupListener, ScoringListener{
	
	private final static Logger log = Logger.getLogger(PHook.class);

	private PTransitRouterFactory pTransitRouterFactory = null;
	private PVehiclesFactory pVehiclesFactory = null;
	
	private AgentsStuckHandlerImpl agentsStuckHandler;
	private PBox pBox;

	private StatsManager statsManager;

	private PersonReRouteStuckFactory stuckFactory;

    public PHook(Controler controler) {
		this(controler, null, null, null, null);
	}
	
	public PHook(Controler controler, PtMode2LineSetter lineSetter, PTransitRouterFactory pTransitRouterFactory, PersonReRouteStuckFactory stuckFactory, Class<? extends TripRouterFactory> tripRouterFactory){
		PConfigGroup pConfig = (PConfigGroup) controler.getConfig().getModule(PConfigGroup.GROUP_NAME);
		this.pBox = new PBox(pConfig);
		this.pTransitRouterFactory = pTransitRouterFactory;
		if (this.pTransitRouterFactory == null) {
			this.pTransitRouterFactory = new PTransitRouterFactory(pConfig.getPtEnabler());
		}
		// When setting a TransitRouterFactory and also a TripRouterFactory in the controler a RuntimeException is thrown.
//		controler.setTransitRouterFactory(this.pTransitRouterFactory);
		controler.setMobsimFactory(new PQSimFactory());
		this.pVehiclesFactory = new PVehiclesFactory(pConfig);

		if(pConfig.getReRouteAgentsStuck()){
			this.agentsStuckHandler = new AgentsStuckHandlerImpl();
			if(stuckFactory == null){
				this.stuckFactory = new PersonReRouteStuckFactoryImpl();
			}else{
				this.stuckFactory = stuckFactory;
			}
		}
		
		controler.setTripRouterFactory(PTripRouterFactoryFactory.getTripRouterFactoryInstance(controler, tripRouterFactory, this.pTransitRouterFactory));
		this.statsManager = new StatsManager(controler, pConfig, this.pBox, lineSetter); 
	}
	
	@Override
	public void notifyStartup(StartupEvent event) {
		this.statsManager.notifyStartup(event);
		this.pBox.notifyStartup(event);
        addPTransitScheduleToOriginalOne(event.getControler().getScenario().getTransitSchedule(), this.pBox.getpTransitSchedule());
		addPVehiclesToOriginalOnes(event.getControler().getScenario().getVehicles(), this.pVehiclesFactory.createVehicles(this.pBox.getpTransitSchedule()));

		this.pTransitRouterFactory.createTransitRouterConfig(event.getControler().getConfig());
		this.pTransitRouterFactory.updateTransitSchedule(event.getControler().getScenario().getTransitSchedule());
		
		if(this.agentsStuckHandler != null){
			event.getControler().getEvents().addHandler(this.agentsStuckHandler);
		}
	}

	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		final Controler controler = event.getControler();
		if(event.getIteration() == controler.getConfig().controler().getFirstIteration()){
			log.info("This is the first iteration. All lines were added by notifyStartup event.");
		} else {
			this.pBox.notifyIterationStarts(event);
            removePreviousPTransitScheduleFromOriginalOne(event.getControler().getScenario().getTransitSchedule());
			addPTransitScheduleToOriginalOne(event.getControler().getScenario().getTransitSchedule(), this.pBox.getpTransitSchedule());
			removePreviousPVehiclesFromScenario(event.getControler().getScenario().getVehicles());
            addPVehiclesToOriginalOnes(event.getControler().getScenario().getVehicles(), this.pVehiclesFactory.createVehicles(this.pBox.getpTransitSchedule()));

			this.pTransitRouterFactory.updateTransitSchedule(event.getControler().getScenario().getTransitSchedule());
			
			if(this.agentsStuckHandler != null){
				ParallelPersonAlgorithmRunner.run(controler.getPopulation(), controler.getConfig().global().getNumberOfThreads(), new ParallelPersonAlgorithmRunner.PersonAlgorithmProvider() {
					@Override
					public AbstractPersonAlgorithm getPersonAlgorithm() {
						return stuckFactory.getReRouteStuck(new PlanRouter(
						controler.getTripRouterFactory().instantiateAndConfigureTripRouter(),
						controler.getScenario().getActivityFacilities()
						), ((ScenarioImpl)controler.getScenario()), agentsStuckHandler.getAgentsStuck());
					}
				});
			}
		}
		this.dumpTransitScheduleAndVehicles(event.getControler(), event.getIteration());
	}

    @Override
	public void notifyScoring(ScoringEvent event) {
		this.pBox.notifyScoring(event);	
	}

    private Set<Id<TransitStopFacility>> currentPFacilityIDs = new HashSet<>();
    private Set<Id<TransitLine>> currentPTransitLineIDs = new HashSet<>();

	private void addPTransitScheduleToOriginalOne(TransitSchedule baseSchedule, TransitSchedule pSchedule) {
		if(pSchedule == null){
			log.info("pSchedule does not exist, doing nothing");
            return;
		}
		for (TransitStopFacility pStop : pSchedule.getFacilities().values()) {
            if (!baseSchedule.getFacilities().containsKey(pStop.getId())) {
                baseSchedule.addStopFacility(pStop);
                currentPFacilityIDs.add(pStop.getId());
            }
		}
		for (TransitLine pLine : pSchedule.getTransitLines().values()) {
            if (!baseSchedule.getTransitLines().containsKey(pLine.getId())) {
                baseSchedule.addTransitLine(pLine);
                currentPTransitLineIDs.add(pLine.getId());
            }
		}
	}

    private void removePreviousPTransitScheduleFromOriginalOne(TransitSchedule transitSchedule) {
        for (Id<TransitLine> transitLineId : currentPTransitLineIDs) {
            transitSchedule.removeTransitLine(transitSchedule.getTransitLines().get(transitLineId));
        }
        currentPTransitLineIDs.clear();
        for (Id<TransitStopFacility> facilityId : currentPFacilityIDs) {
            transitSchedule.removeStopFacility(transitSchedule.getFacilities().get(facilityId));
        }
        currentPFacilityIDs.clear();
    }

    private Set<Id<VehicleType>> currentPVehicleTypeIDs = new HashSet<>();
    private Set<Id<Vehicle>> currentPVehicleIDs = new HashSet<>();

	private void addPVehiclesToOriginalOnes(Vehicles baseVehicles, Vehicles pVehicles){
		for (VehicleType t : pVehicles.getVehicleTypes().values()) {
            if (!baseVehicles.getVehicleTypes().containsKey(t.getId())) {
                baseVehicles.addVehicleType(t);
                currentPVehicleTypeIDs.add(t.getId());
            }
		}
		for (Vehicle v : pVehicles.getVehicles().values()) {
            if (!baseVehicles.getVehicles().containsKey(v.getId())) {
                baseVehicles.addVehicle(v);
                currentPVehicleIDs.add(v.getId());
            }
		}
	}

    private void removePreviousPVehiclesFromScenario(Vehicles vehicles) {
        for (Id<Vehicle> vehicleId : currentPVehicleIDs) {
            vehicles.removeVehicle(vehicleId);
        }
        currentPVehicleIDs.clear();
        for (Id<VehicleType> vehicleTypeId : currentPVehicleTypeIDs) {
            vehicles.removeVehicleType(vehicleTypeId);
        }
        currentPVehicleTypeIDs.clear();
    }
	
	private void dumpTransitScheduleAndVehicles(Controler controler, int iteration){
		TransitScheduleWriterV1 writer = new TransitScheduleWriterV1(controler.getScenario().getTransitSchedule());
		VehicleWriterV1 writer2 = new VehicleWriterV1(controler.getScenario().getVehicles());
		writer.write(controler.getControlerIO().getIterationFilename(iteration, "transitSchedule.xml.gz"));
		writer2.writeFile(controler.getControlerIO().getIterationFilename(iteration, "vehicles.xml.gz"));
	}
}