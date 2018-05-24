package cn.edu.seu.kse.lpmln.solver.parallel.augmentedsubsetway;

import cn.edu.seu.kse.lpmln.model.AugmentedSubset;
import cn.edu.seu.kse.lpmln.model.ExperimentReport;
import cn.edu.seu.kse.lpmln.model.WeightedAnswerSet;
import cn.edu.seu.kse.lpmln.solver.LPMLNSolver;
import cn.edu.seu.kse.lpmln.solver.impl.LPMLNBaseSolver;
import cn.edu.seu.kse.lpmln.util.LpmlnThreadPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author 许鸿翔
 * @date 2018/1/17
 */
public class AugmentedSolver extends LPMLNBaseSolver implements Runnable {
    //通过增强子集 并行推理
    //Wang B, Zhang Z. A Parallel LP^{MLN Solver: Primary Report[C]// Aspocp. 2017.
    //允许的子程序数量
    private int threadNums;
    private List<String> translatedFiles;
    private List<LPMLNSolver> subsetSolvers;
    private boolean deleteTranslatedFiles = true;
    private LpmlnThreadPool threadPool;
    private AugmentedSubsetPartitioner partitioner;
    private List<AugmentedSubset> augmentedSubsets;
    private Logger logger = LogManager.getLogger(AugmentedSolver.class.getName());
    private String arch;

    public AugmentedSolver(){
        translatedFiles = new ArrayList<>();
        threadNums = Runtime.getRuntime().availableProcessors();
        subsetSolvers = new ArrayList<>();
        augmentedSubsets = new ArrayList<>();
        //设定划分方式
        partitioner = new AugmentedSubsetPartitioner();
    }

    public AugmentedSolver(String arch){
        translatedFiles = new ArrayList<>();
        threadNums = Runtime.getRuntime().availableProcessors();
        subsetSolvers = new ArrayList<>();
        augmentedSubsets = new ArrayList<>();
        //设定划分方式
        partitioner = new AugmentedSubsetPartitioner();
        this.arch = arch;
    }

    @Override
    public List<WeightedAnswerSet> solve(File ruleFile) {
        //开始计时
        totalTime.start();

        solveTime.start();
        //解析LPMLN程序
        lpmlnProgram = parse(ruleFile);
        solveTime.stop();
        return solveProgram(lpmlnProgram);
    }

    @Override
    public void executeSolving(){
        threadPool = new LpmlnThreadPool("AugmentedSolver");
        //拆分为多个增强子集
        augmentedSubsets = partitioner.partition(lpmlnProgram,threadNums);

        solveTime.start();
        subsetSolvers.clear();
        //增强子集求解
        augmentedSubsets.forEach(subset -> {
            LPMLNSolver subsetSolver;
            if(arch==null||arch.replaceAll("D","").length()==0){
                subsetSolver = new AugmentedSubsetSolver(subset);
            }else{
                subsetSolver = chooseSolver(arch);
                subsetSolver.setLpmlnProgram(subset.toLpmlnProgram());
            }
            subsetSolver.setFiltResult(false);
            subsetSolver.setCalculatePossibility(false);
            subsetSolvers.add(subsetSolver);
            threadPool.execute(subsetSolver);
        });

        //等待增强子集求解完成
        threadPool.waitDone();
        solveTime.stop();

        totalTime.stop();
        weightedAs = collectWas();
    }

    protected List<WeightedAnswerSet> collectWas(){
        //收集过滤回答集
        List<WeightedAnswerSet> collectedWas = new ArrayList<>();
        subsetSolvers.forEach(solver->{
            //logger.debug(solver.getAllWeightedAs().size()+" aug was collected");
            collectedWas.addAll(solver.getAllWeightedAs());
        });
        return collectedWas;
    }

    @Override
    public ExperimentReport getReport(){
        super.getReport();
        report.setProcessors(String.valueOf(threadNums));
        return report;
    }

    public List<String> getTranslatedFiles() {
        return translatedFiles;
    }

    public void setTranslatedFiles(List<String> translatedFiles) {
        this.translatedFiles = translatedFiles;
    }

    public int getThreadNums() {
        return threadNums;
    }

    public void setThreadNums(int threadNums) {
        this.threadNums = threadNums;
    }

    public boolean isDeleteTranslatedFiles() {
        return deleteTranslatedFiles;
    }

    public void setDeleteTranslatedFiles(boolean deleteTranslatedFiles) {
        this.deleteTranslatedFiles = deleteTranslatedFiles;
    }

    public void setPolicy(AugmentedSubsetPartitioner.SPLIT_TYPE policy) {
        this.partitioner.setPolicy(policy);
    }
}
