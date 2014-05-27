/**
 * Copyright (c) 2012-2013, Imperial College London, developed under the MODAClouds, FP7 ICT Project, grant agreement n�� 318484
 * All rights reserved.
 * 
 *  Contact: imperial <weikun.wang11@imperial.ac.uk>
 *
 *    Licensed under the GNU GENERAL PUBLIC LICENSE, Version 3 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://http://www.gnu.org/licenses/gpl.html
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package imperial.modaclouds.monitoring.sda.weka;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import polimi.deib.csparql_rest_api.exception.ServerErrorException;
import polimi.deib.csparql_rest_api.exception.StreamErrorException;

import imperial.modaclouds.monitoring.data_retriever.Client_Server;
import it.polimi.modaclouds.monitoring.ddaapi.DDAConnector;
import it.polimi.modaclouds.monitoring.ddaapi.ValidationErrorException;
import it.polimi.modaclouds.monitoring.kb.api.KBConnector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.KBEntity;
import it.polimi.modaclouds.qos_models.monitoring_ontology.MonitorableResource;
import it.polimi.modaclouds.qos_models.monitoring_ontology.Parameter;
import it.polimi.modaclouds.qos_models.monitoring_ontology.StatisticalDataAnalyzer;

public class WekaSDA {

	/**
	 * Knowledge base connector.
	 */
	private static KBConnector kbConnector;
	
	/**
	 * DDa connector.
	 */
	private static DDAConnector ddaConnector;
	
	/**
	 * The unique monitored resource ID.
	 */
	private static String monitoredResourceID;

	/**
	 * @param args
	 */
	public static void main(String[] args) {	
		
		monitoredResourceID = "FrontendVM";
		
		try {
			//objectStoreConnector = ObjectStoreConnector.getInstance();
			//MO.setKnowledgeBaseURL(objectStoreConnector.getKBUrl());
			ddaConnector = DDAConnector.getInstance();
			kbConnector = KBConnector.getInstance();
			Client_Server.retrieve(Integer.valueOf(args[0]));
			//kbConnector.setKbURL(new URL(MO.getKnowledgeBaseDataURL()));
		} catch (Exception e) {
			e.printStackTrace();
		}

		ArrayList<Set<Parameter>> parameters = new ArrayList<Set<Parameter>>();
		ArrayList<String> returnedMetric = new ArrayList<String>();
		ArrayList<String> targetMetric = new ArrayList<String>();
		ArrayList<String> targetResources = new ArrayList<String>();
		ArrayList<String> type = new ArrayList<String>();
		ArrayList<Integer> period = new ArrayList<Integer>();
		ArrayList<Integer> nextPauseTime = new ArrayList<Integer>();

		long startTime = 0;

		while(true) {

			if (System.currentTimeMillis() - startTime > 10000) {

				Set<KBEntity> dcConfig = kbConnector.getAll(StatisticalDataAnalyzer.class);

				for (KBEntity kbEntity: dcConfig) {
					StatisticalDataAnalyzer sdas = (StatisticalDataAnalyzer) kbEntity;

					//dc.setEnabled(true);
					//kbConnector.add(dc);

					parameters.add(sdas.getParameters());

					returnedMetric.add(sdas.getReturnedMetric());

					targetMetric.add(sdas.getTargetMetric());

					type.add(sdas.getAggregateFunction());

					for (Parameter par: sdas.getParameters()) {
						switch (par.getName()) {
						case "timeStep":
							period.add(Integer.valueOf(par.getValue())*1000);
							nextPauseTime.add(Integer.valueOf(par.getValue())*1000);
							break;
						}
					}

					for (MonitorableResource resource: sdas.getTargetResources()) {
						targetResources.add(resource.getId());
					}

				}
				
				if (dcConfig.size() == 0) {
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					continue;
				}
				
				startTime = System.currentTimeMillis();
			}

			int toSleep = Collections.min(nextPauseTime);
			int index = nextPauseTime.indexOf(toSleep);

			try {
				Thread.sleep(Math.max(toSleep, 0));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			double value = -1;
			
			switch(type.get(index)) {
			case "ForecastingML":
				TimeSeriesForecasting forecasting = new TimeSeriesForecasting();
				value = forecasting.forecast(targetResources.get(index), targetMetric.get(index), parameters.get(index));
				break;
			case "Correlation":
				Correlation correlation = new Correlation();
				value = correlation.correlate(targetResources.get(index), targetMetric.get(index), parameters.get(index));
				break;
			}
			
			if (Math.abs(value + 1) >= 0.00001) {
				System.out.println("value: "+value);
				try {
					ddaConnector.sendSyncMonitoringDatum(String.valueOf(value), returnedMetric.get(index), monitoredResourceID);
				} catch (ServerErrorException | StreamErrorException
						| ValidationErrorException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
