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

import imperial.modaclouds.monitoring.data_retriever.Client_Server;
import imperial.modaclouds.monitoring.data_retriever.ValueSet;
import it.polimi.modaclouds.qos_models.monitoring_ontology.Parameter;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.SMOreg;
import weka.core.Attribute;
import weka.core.Instances;


public class Correlation {
	
public double correlate(String targetResource, String targetMetric, Set<Parameter> parameters) {
		
		ArrayList<ArrayList<String>> timestamps = new ArrayList<ArrayList<String>>();
		ArrayList<ArrayList<String>> data = new ArrayList<ArrayList<String>>();
		
		System.out.println(targetResource);
		System.out.println(targetMetric);
		
		ValueSet targetData = Client_Server.obtainData(targetResource, targetMetric);
		
		if (targetData == null) {
			System.out.println("No data received");
			return -1;
		}
		
		timestamps.add(targetData.getTimestamps());
		data.add(targetData.getValues());
		
		ArrayList<String> metricName = new ArrayList<String>();
		metricName.add("time");
		int index = targetResource.lastIndexOf("/");
		metricName.add(targetResource.substring(index+1)+targetMetric);
		
		int predictionStep = 0;
		String otherTarget1 = null;
		String otherTarget2 = null;
		String otherMetric1 = null;
		String otherMetric2 = null;
		String method = null;
		int isTraining = 0;
		
		for (Parameter par: parameters) {
			switch (par.getName()) {
			case "predictionStep":
				predictionStep = Integer.valueOf(par.getValue());
				break;
			case "otherTarget1":
				otherTarget1 = par.getValue();
				System.out.println("otherTarget1: "+otherTarget1);
				break;
			case "otherTarget2":
				otherTarget2 = par.getValue();
				break;
			case "otherMetric1":
				otherMetric1 = par.getValue();
				System.out.println("otherMetric1: "+otherMetric1);
				break;
			case "otherMetric2":
				otherMetric2 = par.getValue();
				break;
			case "method":
				method = par.getValue();
				break;
			case "isTraining":
				isTraining = Integer.valueOf(par.getValue());
				System.out.println("isTraining: "+isTraining);
				break;
			}
		}
		
		if (otherTarget1 != null) {
			ValueSet temp = Client_Server.obtainData(otherTarget1, otherMetric1);
			if (temp == null) {
				System.out.println("No additional data received");
				return -1;
			}
			timestamps.add(temp.getTimestamps());
			data.add(temp.getValues());
			index = otherTarget1.lastIndexOf("/");
			metricName.add(otherTarget1.substring(index+1)+otherMetric1);
		}
		
		if (otherTarget2 != null) {
			ValueSet temp = Client_Server.obtainData(otherTarget2, otherMetric2);
			timestamps.add(temp.getTimestamps());
			data.add(temp.getValues());
			index = otherTarget2.lastIndexOf("/");
			metricName.add(otherTarget2.substring(index+1)+otherMetric2);
		}
		
		if (isTraining == 1) {
			String fileName = metricName.get(1)+"CorrelationTrain.arff";
			CreateArff.create(timestamps, data, metricName, fileName);
			return -1;
		}
		else {
			String fileName = metricName.get(1)+"CorrelationTest.arff";
			CreateArff.create(timestamps, data, metricName, fileName);
			
			double[] value = compute(metricName.get(1)+"CorrelationTrain.arff", metricName.get(1)+"CorrelationTest.arff", method, metricName.get(1));

			System.out.println("Correlation result: "+ Arrays.toString(value));
			
			return value[0];
			
			
		}
		
		
	}
	
	/**
	 * This method calculates the target metrics using correlation algorithm with the weka library.
	 * 
	 * @param trainFile	the train file for weka
	 * @param testFile	the test file for weka to get the target metric
	 * @param method	the correlation algorithm for weka
	 * @param target	the target metric
	 * @return	the value of the target metric
	 */
	public double[] compute(String trainFile, String testFile, String method, String target) {

		double correlated[] = null;			
		
		try {
			Instances train = new Instances(new BufferedReader(new FileReader(trainFile)));
			Instances test = new Instances(new BufferedReader(new FileReader(testFile)));

			train.setClass(new Attribute(target));

			train.setClassIndex(train.numAttributes() - 1);
			test.setClassIndex(test.numAttributes() - 1);

			Classifier cls = null;
			switch(method) {
			case "LinearRegression":
				cls = new LinearRegression();
				break;
			case "SMO":
				cls = new SMO();
				break;
			case "SMOreg":
				cls = new SMOreg();
				break;
			case "NaiveBayes":
				cls = new NaiveBayes();
				break;
			}
			
			cls.buildClassifier(train);
			
			correlated = new double[test.numInstances()];

			for (int i = 0; i < test.numInstances(); i++) {
				correlated[i] = cls.classifyInstance(test.instance(i));
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return correlated;
	} 

}
