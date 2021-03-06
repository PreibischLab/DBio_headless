package net.preibisch.dbio_headless;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.preibisch.distribution.algorithm.blockmanagement.blockinfo.BasicBlockInfo;
import net.preibisch.distribution.algorithm.blockmanagement.blockinfo.BasicBlockInfoGenerator;
import net.preibisch.distribution.algorithm.controllers.items.Job;
import net.preibisch.distribution.algorithm.controllers.items.Metadata;
import net.preibisch.distribution.algorithm.errorhandler.logmanager.MyLogger;
import net.preibisch.distribution.algorithm.multithreading.Threads;
import net.preibisch.distribution.io.img.ImgFile;
import net.preibisch.distribution.io.img.n5.N5File;
import net.preibisch.distribution.io.img.xml.XMLFile;
import net.preibisch.distribution.tools.helpers.ArrayHelpers;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;

public class TestFusionSpimInBlocksN5 {
	public static void main(String[] args) throws IOException, SpimDataException {
		
		final String input_path = "/home/mzouink/Desktop/testn5/dataset.xml";
		final String output_path = "/home/mzouink/Desktop/testn5/back_ output45.n5";

		new ImageJ();

		XMLFile inputFile = XMLFile.XMLFile(input_path);

		ImageJFunctions.show(inputFile.getImg(), "Input");

		MyLogger.log().info("BB: " + inputFile.bb().toString());
		MyLogger.log().info("Dims: " + Util.printCoordinates(inputFile.getDims()));

		N5File outputFile = new N5File(output_path, inputFile.getDims());
		MyLogger.log().info("Blocks: " + Util.printCoordinates(outputFile.getBlocksize()));

		Map<Integer, BasicBlockInfo> blocks = BasicBlockInfoGenerator.divideIntoBlockInfo(inputFile.bb());

		long[] bsizes = ArrayHelpers.fill(BasicBlockInfoGenerator.BLOCK_SIZE, inputFile.getDimensions().length);
		Metadata md = new Metadata(Job.get().getId(),input_path,output_path, new BoundingBox(inputFile.bb()) ,bsizes, blocks);
		int total = md.getBlocksInfo().size();
		System.out.println(md.toString());

		outputFile.create();

		ImageJFunctions.show(outputFile.fuse(), "Black output");

		ExecutorService executor = Threads.createExService();

		for (int i = 0; i < total; i++) {
			executor.submit(new Task(i, inputFile, outputFile, md.getBlocksInfo().get(i)));
		}
	}
}

class Task implements Runnable {
	private int i;
	private XMLFile input;
	private N5File output;
	private BasicBlockInfo binfo;

	public Task(int i, ImgFile input, N5File output, BasicBlockInfo binfo) {
		this.i = i;
		this.input = (XMLFile) input;
		this.output = output;
		this.binfo = (BasicBlockInfo) binfo;
	}

	public void run() {
		try {
			MyLogger.log().info("Started " + i);
			BoundingBox bb = new BoundingBox(Util.long2int(binfo.getMin()), Util.long2int(binfo.getMax()));
			RandomAccessibleInterval<FloatType> block = input.fuse(bb, 0);
			output.saveBlock(block, binfo.getGridOffset());
			MyLogger.log().info("Block " + i + " saved !");
		} catch (IOException e) {
			MyLogger.log().error("ERROR: Block " + i);
			e.printStackTrace();
		}
	}

}
