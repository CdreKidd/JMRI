// TrainSwitchLists.java

package jmri.jmrit.operations.trains;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import jmri.jmrit.operations.locations.Location;
import jmri.jmrit.operations.rollingstock.cars.Car;
import jmri.jmrit.operations.rollingstock.cars.CarManager;
import jmri.jmrit.operations.rollingstock.engines.Engine;
import jmri.jmrit.operations.rollingstock.engines.EngineManager;
import jmri.jmrit.operations.routes.Route;
import jmri.jmrit.operations.routes.RouteLocation;
import jmri.jmrit.operations.setup.Setup;

/**
 * Builds a switch list for a location on the railroad
 * @author Daniel Boudreau (C) Copyright 2008, 2011, 2012
 *
 */
public class TrainSwitchLists extends TrainCommon {
	
	static ResourceBundle rb = ResourceBundle.getBundle("jmri.jmrit.operations.trains.JmritOperationsTrainsBundle");
	
	TrainManager manager = TrainManager.instance();
	char formFeed = '\f';
		
	/**
	 * builds a switch list for a location
	 * @param location The Location needing a switch list
	 */
	public void buildSwitchList(Location location){
		// Append switch list data if not operating in real time
		boolean newTrainsOnly = !Setup.isSwitchListRealTime();
		boolean append = false;
		boolean nextTrain = false;
		if (newTrainsOnly){
			if (!location.getStatus().equals(Location.MODIFIED))
				return;	// nothing to add
			append = location.getSwitchListState() == Location.SW_APPEND;
			if (location.getSwitchListState() != Location.SW_APPEND)
				location.setSwitchListState(Location.SW_APPEND);
			location.setStatus(Location.UPDATED);
		}
		
		// load message formats
		pickupUtilityMessageFormat = Setup.getSwitchListPickupUtilityCarMessageFormat();
		setoutUtilityMessageFormat = Setup.getSwitchListSetoutUtilityCarMessageFormat();
		localUtilityMessageFormat = Setup.getSwitchListLocalUtilityCarMessageFormat();

		// create manifest file
		File file = TrainManagerXml.instance().createSwitchListFile(location.getName());
		
		PrintWriter fileOut;
		try {
			fileOut = new PrintWriter(new BufferedWriter(new FileWriter(file, append)), true);
		} catch (IOException e) {
			log.error("can not open switchlist file");
			return;
		}
		// build header
		if (!append)
			newLine(fileOut, Setup.getRailroadName());
		newLine(fileOut);
		newLine(fileOut, MessageFormat.format(rb.getString("SwitchListFor"), new Object[]{splitString(location.getName())}));
		
		String valid = MessageFormat.format(rb.getString("Valid"), new Object[]{getDate()});
		
		if (Setup.isPrintTimetableNameEnabled()){
			TrainSchedule sch = TrainScheduleManager.instance().getScheduleById(manager.getTrainScheduleActiveId());
			if (sch != null)
				valid = valid + " ("+sch.getName()+")"; 
		}
		if (Setup.isPrintValidEnabled())
			newLine(fileOut, valid);
		
		if (!location.getSwitchListComment().equals(""))
			newLine(fileOut, location.getSwitchListComment());
		
		// get a list of trains sorted by arrival time
		List<Train> trains = getTrainsArrivingThisLocationList(location);
		CarManager carManager = CarManager.instance();
		EngineManager engineManager = EngineManager.instance();
		for (int i=0; i<trains.size(); i++){
			Train train = trains.get(i);
			if (!train.isBuilt())
				continue;	// train wasn't built so skip
			if (newTrainsOnly && train.getSwitchListStatus().equals(Train.PRINTED))
				continue;	// already printed this train
			Route route = train.getRoute();
			if (route == null)
				continue;	// no route for this train
			// get engine and car lists
			List<String> enginesList = engineManager.getByTrainList(train);
			List<String> carList = carManager.getByTrainDestinationList(train);
			// determine if train works this location
			boolean works = false;
			List<String> routeList = route.getLocationsBySequenceList();
			for (int r=0; r<routeList.size(); r++){
				RouteLocation rl = route.getLocationById(routeList.get(r));
				if (splitString(rl.getName()).equals(splitString(location.getName()))){
					for (int k = 0; k < carList.size(); k++) {
						Car car = carManager.getById(carList.get(k));
						if (car.getRouteLocation() == rl || car.getRouteDestination() == rl){
							works = true;
							break;
						}
					}
					for (int k = 0; k < enginesList.size(); k++) {
						Engine eng = engineManager.getById(enginesList.get(k));
						if (eng.getRouteLocation() == rl || eng.getRouteDestination() == rl){
							works = true;
							break;
						}
					}
					if (works)
						break;
				}
			}		
			if (!works && !Setup.isSwitchListAllTrainsEnabled()) {
				log.debug("No work for train ("+train.getName()+") at location "+location.getName());
				continue;
			}
			if (nextTrain && Setup.isSwitchListPagePerTrainEnabled())
				fileOut.write(formFeed);
			nextTrain = true;
			// some cars counts and the number of times this location get's serviced
			int pickupCars = 0;
			int dropCars = 0;
			int stops = 1;
			boolean trainDone = false;
			// does the train stop once or more at this location?
			for (int r=0; r<routeList.size(); r++){
				RouteLocation rl = route.getLocationById(routeList.get(r));
				if (splitString(rl.getName()).equals(splitString(location.getName()))){
					String expectedArrivalTime = train.getExpectedArrivalTime(rl);
					if (expectedArrivalTime.equals("-1")){
						trainDone = true;
					}
					if (stops > 1){
						// Print visit number only if previous location wasn't the same
						RouteLocation rlPrevious = route.getLocationById(routeList.get(r-1));
						if (!splitString(rl.getName()).equals(splitString(rlPrevious.getName()))){
							newLine(fileOut);
							if (train.isTrainInRoute()){
								if (expectedArrivalTime.equals("-1"))
									newLine(fileOut, MessageFormat.format(rb.getString("VisitNumberDone"), new Object[]{stops, train.getIconName()}));
								else if (r != routeList.size()-1)
									newLine(fileOut, MessageFormat.format(rb.getString("VisitNumberDeparted"), new Object[]{stops, train.getIconName(), expectedArrivalTime, rl.getTrainDirectionString()}));
								else
									newLine(fileOut, MessageFormat.format(rb.getString("VisitNumberTerminatesDeparted"), new Object[]{stops, train.getIconName(), expectedArrivalTime, splitString(rl.getName())}));
							} else {
								if (r != routeList.size()-1)
									newLine(fileOut, MessageFormat.format(rb.getString("VisitNumber"), new Object[]{stops, train.getIconName(), expectedArrivalTime, rl.getTrainDirectionString()}));
								else
									newLine(fileOut, MessageFormat.format(rb.getString("VisitNumberTerminates"), new Object[]{stops, train.getIconName(), expectedArrivalTime, splitString(rl.getName())}));
							}
						} else {
							stops--;	// don't bump stop count, same location
							// Does the train reverse direction?
							if (rl.getTrainDirection() != rlPrevious.getTrainDirection())
								newLine(fileOut, MessageFormat.format(rb.getString("TrainDirectionChange"), new Object[]{train.getIconName(), rl.getTrainDirectionString()}));
						}
					} else {
						newLine(fileOut);
						newLine(fileOut, MessageFormat.format(rb.getString("ScheduledWork"), new Object[]{train.getIconName(), train.getDescription()}));										
						if (train.isTrainInRoute()){
							if (!trainDone){
								newLine(fileOut, MessageFormat.format(rb.getString("DepartedExpected"), new Object[]{splitString(train.getTrainDepartsName()), expectedArrivalTime, rl.getTrainDirectionString()}));
							}
						} else {
							if (r == 0 && routeList.size()>1)
								newLine(fileOut, MessageFormat.format(rb.getString("DepartsAt"), new Object[]{splitString(train.getTrainDepartsName()), rl.getTrainDirectionString(), train.getFormatedDepartureTime()}));
							else if (routeList.size()>1)
								newLine(fileOut, MessageFormat.format(rb.getString("DepartsAtExpectedArrival"), new Object[]{splitString(train.getTrainDepartsName()), train.getFormatedDepartureTime(), expectedArrivalTime, rl.getTrainDirectionString()}));
						}
					}
					// go through the list of engines and determine if the engine departs here					
					pickupEngines(fileOut, enginesList, rl, Setup.getSwitchListOrientation());

					// block cars by destination
					for (int j = 0; j < routeList.size(); j++) {						
						RouteLocation rld = train.getRoute().getLocationById(routeList.get(j));
						utilityCarTypes.clear();	// list utility cars by quantity
						for (int k = 0; k < carList.size(); k++) {
							Car car = carManager.getById(carList.get(k));
							if (car.getRouteLocation() == rl && !car.getTrackName().equals("")
									&& car.getRouteDestination() == rld) {
								if (car.isUtility())
									pickupCars(fileOut, carList, car, rl, rld);
								else
									switchListPickUpCar(fileOut, car);
								pickupCars++;
							}
						}
					}
					
					utilityCarTypes.clear();	// list utility cars by quantity
					for (int j=0; j<carList.size(); j++){
						Car car = carManager.getById(carList.get(j));
						if (car.getRouteDestination() == rl){
							if (car.isUtility())
								setoutCars(fileOut, carList, car, rl, car.getRouteLocation().equals(car.getRouteDestination()) && car.getTrack()!=null);
							else
								switchListDropCar(fileOut, car);
							dropCars++;
						}
					}
					
					dropEngines(fileOut, enginesList, rl, Setup.getSwitchListOrientation());
					stops++;
				}
			}
			if (trainDone && pickupCars == 0 && dropCars == 0){
				newLine(fileOut, rb.getString("TrainDone"));
			} else {
				if (stops > 1 && pickupCars == 0){
					newLine(fileOut, rb.getString("NoCarPickUps"));
				}
				if (stops > 1 && dropCars == 0){
					newLine(fileOut, rb.getString("NoCarDrops"));
				}
			}
		}
		// Are there any cars that need to be found?
		getCarsLocationUnknown(fileOut);
		fileOut.flush();
		fileOut.close();
	}

	public void printSwitchList(Location location, boolean isPreview) {
		File buildFile = TrainManagerXml.instance().getSwitchListFile(location.getName());
		if (isPreview && Setup.isManifestEditorEnabled())
			TrainPrintUtilities.openDesktopEditor(buildFile);
		else
			TrainPrintUtilities.printReport(buildFile, rb.getString("SwitchList")
					+ " " + location.getName(), isPreview, Setup.getFontName(),
					false, Setup.getManifestLogoURL(), location.getDefaultPrinterName(), Setup.getSwitchListOrientation());
	}
	
	/**
	 * Provides a list of trains ordered by arrival time to this location
	 * @param location The location
	 * @return A list of trains ordered by arrival time.
	 */
	private List<Train> getTrainsArrivingThisLocationList(Location location){
		// get a list of trains
		List<String> trainIds = manager.getTrainsByTimeList();
		List<Train> trains = new ArrayList<Train>();
		List<Integer>arrivalTimes = new ArrayList<Integer>();
		for (int i=0; i<trainIds.size(); i++){
			Train train = manager.getTrainById(trainIds.get(i));
			if (!train.isBuilt())
				continue;	// train wasn't built so skip
			Route route = train.getRoute();
			if (route == null)
				continue;	// no route for this train
			List<String> routeList = route.getLocationsBySequenceList();
			for (int r=0; r<routeList.size(); r++){
				RouteLocation rl = route.getLocationById(routeList.get(r));
				if (splitString(rl.getName()).equals(splitString(location.getName()))){
					int expectedArrivalTime = train.getExpectedTravelTimeInMinutes(rl);
					// is already serviced then "-1"
					if (expectedArrivalTime == -1){
						trains.add(0, train);	// place all trains that have already been serviced at the start
						arrivalTimes.add(0, expectedArrivalTime);
					}
					// if the train is in route, then expected arrival time is in minutes
					else if (train.isTrainInRoute()){
						for (int j=0; j<trains.size(); j++){
							Train t = trains.get(j);
							int time = arrivalTimes.get(j);
							if (t.isTrainInRoute() && expectedArrivalTime < time){
								trains.add(j, train);
								arrivalTimes.add(j, expectedArrivalTime);
								break;
							} 
							if (!t.isTrainInRoute()) {
								trains.add(j, train);
								arrivalTimes.add(j, expectedArrivalTime);
								break;
							}
						}
					// Train has not departed
					} else {
						for (int j=0; j<trains.size(); j++){
							Train t = trains.get(j);
							int time = arrivalTimes.get(j);
							if (!t.isTrainInRoute() && expectedArrivalTime < time){
								trains.add(j, train);
								arrivalTimes.add(j, expectedArrivalTime);
								break;
							}
						}
					}
					if (!trains.contains(train)){
						trains.add(train);
						arrivalTimes.add(expectedArrivalTime);
					}
					break;	// done
				}
			}

		}
		return trains;
	}
	
	protected void newLine (PrintWriter file, String string){
		newLine (file, string, Setup.getSwitchListOrientation());
	}

	static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(TrainSwitchLists.class.getName());
}
