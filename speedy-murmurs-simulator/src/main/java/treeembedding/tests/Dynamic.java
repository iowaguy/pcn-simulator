package treeembedding.tests;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import gtna.data.Series;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.networks.util.ReadableFile;
import gtna.util.Config;

import java.io.File;
import java.io.FilenameFilter;

import treeembedding.RunConfig;
import treeembedding.credit.CreditMaxFlow;
import treeembedding.credit.CreditNetwork;
import treeembedding.credit.partioner.Partitioner;
import treeembedding.credit.partioner.RandomPartitioner;
import treeembedding.treerouting.Treeroute;
import treeembedding.treerouting.TreerouteSilentW;
import treeembedding.treerouting.TreerouteTDRAP;

public class Dynamic {
	
	/**
	 * @param args
	 * 0: run
	 * 1: config (0- SilentWhispers, 7- SpeedyMurmurs, 10-MaxFlow)
	 * 2: steps previously completed  
	 */
	public static void main(String[] args) {
		String runDirPath = args[0] + '/';
		String runConfigPath = runDirPath + "runconfig.yml";

		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

		RunConfig runConfig = null;
		try {
			runConfig = mapper.readValue(new File(runConfigPath), RunConfig.class);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		if (runConfig == null) {
			System.out.println("Unable to parse run configuration file");
			return;
		}

		// General parameters
		Config.overwrite("SKIP_EXISTING_DATA_FOLDERS", Boolean.toString(!runConfig.isForceOverwrite()));
		//String results = "./data/";
		Config.overwrite("MAIN_DATA_FOLDER", runDirPath);
		//String path = "../data/";
		
		int config = runConfig.getRoutingAlgorithm().getId(); // Integer.parseInt(args[1]);
		int step = Integer.parseInt(args[2]);
		String prefix;
		switch (config){
		  case 0: prefix = "SW";break;
		  case 7: prefix = "SM";break;
		  case 10: prefix = "M";break;
		  default: throw new IllegalArgumentException("Routing algoithm not supported");
		}
		String graph, trans, newlinks;
		if(step==0){
			graph =  runConfig.getBasePath()+"/finalSets/dynamic/jan2013-lcc-t0.graph";
			trans = runConfig.getBasePath()+"/finalSets/dynamic/jan2013-trans-lcc-noself-uniq-1.txt";
			newlinks = runConfig.getBasePath()+"/finalSets/dynamic/jan2013-newlinks-lcc-sorted-uniq-t0.txt";
		} else {
			graph = runDirPath+"READABLE_FILE_"+prefix+"-P"+step+"-93502/0/";
			 FilenameFilter fileNameFilter = new FilenameFilter() {
				   
		            @Override
		            public boolean accept(File dir, String name) {
		               if(name.contains("CREDIT_NETWORK") || name.contains("CREDIT_MAX")) {
		                  return true;
		               }
		               return false;
		            }
		         };
			String[] files = (new File(graph)).list(fileNameFilter);
			graph = graph + files[0]+"/graph.txt";
			trans = runConfig.getBasePath()+"/finalSets/dynamic/jan2013-trans-lcc-noself-uniq-"+(step+1)+".txt";
			newlinks = runConfig.getBasePath()+"/finalSets/dynamic/jan2013-newlinks-lcc-sorted-uniq-t"+(step)+".txt";
		}
		switch (config){
		   case 0: runDynSWSM(new String[] {graph, "SW-P"+(step+1),trans ,  newlinks, /* algo */ "0", /* run */ "0"}); break;
		   case 7: runDynSWSM(new String[] {graph, "SM-P"+(step+1), trans ,  newlinks, /* algo */ "7", /* run */ "0"}); break;
		   case 10: runMaxFlow(graph, trans, "M-P"+(step+1), newlinks, 165.55245497208898*1000); break; 
		}

	}
	
	public static void runDynSWSM(String[] args){
		Config.overwrite("SERIES_GRAPH_WRITE", ""+true);
		Config.overwrite("SKIP_EXISTING_DATA_FOLDERS", "false");
		String graph = args[0];
		String name = args[1];
		String trans = args[2];
		String add = args[3];
		int type = Integer.parseInt(args[4]);
		int i = Integer.parseInt(args[5]);
		
		double epoch = 165.55245497208898*1000;
		Treeroute ra;
		boolean dyn;
		boolean multi;
		if (type == 0){
			ra = new TreerouteSilentW();
			multi = true;
			dyn = false;
		} else {
			ra = new TreerouteTDRAP();
			multi = false;
			dyn = true;
		}
		int max = 1;
		double req = 165.55245497208898*2;
		int[] roots ={64,36,43};
		Partitioner part = new RandomPartitioner();
		
		Network net = new ReadableFile(name, name,graph,null);
		CreditNetwork cred = new CreditNetwork(trans, name, epoch, ra,
				dyn, multi, req, part, roots, max, add);
        Series.generate(net, new Metric[]{cred}, i,i);	
	}
	
	public static void runMaxFlow(String graph, String transList, String name, String links,
			double epoch){
		Config.overwrite("SERIES_GRAPH_WRITE", ""+true);
		Config.overwrite("SKIP_EXISTING_DATA_FOLDERS", "false");
		Config.overwrite("MAIN_DATA_FOLDER", "./data/" );
		CreditMaxFlow m = new CreditMaxFlow(transList, name, 
				0, 0,links, epoch); 
		Network test = new ReadableFile(name, name, graph,null);
		Series.generate(test, new Metric[]{m}, 1);
	}

}
