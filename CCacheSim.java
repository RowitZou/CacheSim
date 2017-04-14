import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;
import javax.swing.*;
import javax.swing.border.EtchedBorder;

public class CCacheSim extends JFrame implements ActionListener {

    private JButton execStepBtn, execAllBtn, fileBotton, resetBotton;
    private JComboBox<String> csBox, bsBox, wayBox, replaceBox, prefetchBox,
            writeBox, allocBox, dcsBox, icsBox;
    private JLabel icsLabel, dcsLabel, labelTop, labelLeft, rightLabel, bottomLabel, fileLabel, fileAddrBtn,
            csLabel, bsLabel, wayLabel, replaceLabel, prefetchLabel, writeLabel, allocLabel, fileError;
    private JLabel resultsData[][];
    private JLabel stepLabel[], stepContent[];
    private JRadioButton unifiedCacheButton, separateCacheButton;
    private int read_data_miss = 0, write_data_miss = 0, read_ins_miss = 0;
    private int read_data_hit = 0, write_data_hit = 0, read_ins_hit = 0;
    //打开文件
    private File file;

    //cache
    private Cache cache, dataCache, insCache;

    //指令List
    private ArrayList<Instruction> ins_list = new ArrayList<>();
    //当前是第ip条指令
    private int ip;

    //分别表示左侧几个下拉框所选择的第几项，索引从 0 开始
    private int cacheType, csIndex, bsIndex, wayIndex, replaceIndex, prefetchIndex,
            writeIndex, allocIndex, icsIndex, dcsIndex;
    //其它变量定义
    //...

    /**
     * 构造函数，绘制模拟器面板
     */
    private CCacheSim() {
        super("Cache Simulator");
        String[] cachesize = new String[]{"2KB", "8KB", "32KB", "128KB", "512KB", "2MB"};
        String[] half_cachesize = new String[]{"1KB", "4KB", "16KB", "64KB", "256KB", "1MB"};
        String[] blocksize = new String[]{"16B", "32B", "64B", "128B", "256B"};
        String[] way = new String[]{"直接映象", "2路", "4路", "8路", "16路", "32路"};
        String[] replace = new String[]{"LRU", "FIFO", "RAND"};
        String[] pref = new String[]{"不预取", "不命中预取"};
        String[] write = new String[]{"写回法", "写直达法"};
        String[] alloc = new String[]{"按写分配", "不按写分配"};

        csBox = new JComboBox<>(cachesize);
        icsBox = new JComboBox<>(half_cachesize);
        dcsBox = new JComboBox<>(half_cachesize);
        bsBox = new JComboBox<>(blocksize);
        wayBox = new JComboBox<>(way);
        replaceBox = new JComboBox<>(replace);
        prefetchBox = new JComboBox<>(pref);
        writeBox = new JComboBox<>(write);
        allocBox = new JComboBox<>(alloc);
        draw();
    }

    /**
     * 响应事件，共有三种事件：
     * 1. 执行到底事件
     * 2. 单步执行事件
     * 3. 文件选择事件
     */
    public void actionPerformed(ActionEvent e) {

        Object btn = e.getSource();
        if (btn == execAllBtn || btn == execStepBtn) {
            csBox.setEnabled(false);
            bsBox.setEnabled(false);
            wayBox.setEnabled(false);
            replaceBox.setEnabled(false);
            prefetchBox.setEnabled(false);
            writeBox.setEnabled(false);
            allocBox.setEnabled(false);
            unifiedCacheButton.setEnabled(false);
            separateCacheButton.setEnabled(false);
            icsBox.setEnabled(false);
            dcsBox.setEnabled(false);
        }
        if (btn == execStepBtn) {
            simExecStep(true);
        } else if (btn == execAllBtn) {
            simExecAll();
        } else if (btn == resetBotton) {
            resetAll();
        } else if (btn == fileBotton) {
            JFileChooser fileChoose = new JFileChooser(".");
            int fileOver = fileChoose.showOpenDialog(null);
            if (fileOver == 0) {
                String path = fileChoose.getSelectedFile().getAbsolutePath();
                if (!path.endsWith(".din")) {
                    fileError.setVisible(true);
                } else {
                    clearUI();
                    execAllBtn.setEnabled(false);
                    execStepBtn.setEnabled(false);
                    fileError.setVisible(false);
                    fileAddrBtn.setText(path);
                    file = new File(path);
                    readFile();
                    initCache();
                    execAllBtn.setEnabled(true);
                    execStepBtn.setEnabled(true);
                }
            }
        }
    }

    /**
     * 初始化 Cache 模拟器
     */
    private void initCache() {
        if (cacheType == 0) {
            int cacheSize = 2 * 1024 * (int) Math.pow(4, csIndex);
            int blockSize = 16 * (int) Math.pow(2, bsIndex);
            int wayNum = (int) Math.pow(2, wayIndex);
            cache = new Cache(cacheSize, blockSize, wayNum, replaceIndex, writeIndex);
            dataCache = insCache = null;
        } else {
            int icacheSize = 1024 * (int) Math.pow(4, icsIndex);
            int dcacheSize = 1024 * (int) Math.pow(4, dcsIndex);
            int blockSize = 16 * (int) Math.pow(2, bsIndex);
            int wayNum = (int) Math.pow(2, wayIndex);
            dataCache = new Cache(dcacheSize, blockSize, wayNum, replaceIndex, writeIndex);
            insCache = new Cache(icacheSize, blockSize, wayNum, replaceIndex, writeIndex);
            cache = null;
        }
    }

    /**
     * 将指令和数据流从文件中读入
     */
    private void readFile() {
        try {
            Scanner scan = new Scanner(file);
            ins_list.clear();
            ip = 0;
            while (scan.hasNextLine()) {
                Integer ins_type = scan.nextInt();
                String ins_addr_hex = scan.nextLine().substring(1);
                ins_list.add(new Instruction(ins_type, ins_addr_hex));
            }
            scan.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * 模拟单步执行
     */
    private void simExecStep(boolean oneStep) {
        boolean hitornot;
        //若ip大于指令的总数，重新开始执行
        if (ip >= ins_list.size()) {
            ip = 0;
            initCache();
            return;
        }
        //在执行第一条指令之前，初始化cache
        if (ip == 0) {
            initCache();
        }
        //从ins_list中，取出第ip条指令
        Instruction ins = ins_list.get(ip);
        int ins_type = ins.getInsType(); //指令类型
        int index;                       //指令所在的组号
        int tag;                         //指令在组内分配的块号

        //单一cache
        if (cacheType == 0) {
            index = ins.getIndex(cache.getGroupNum(), cache.getBlockSize());
            tag = ins.getTag(cache.getGroupNum(), cache.getBlockSize());
            //读数据
            if (ins_type == 0) {
                //检测是否命中
                hitornot = cache.read(tag, index);
                //不命中则记录miss数，同时进行cache替换
                if (!hitornot) {
                    read_data_miss++;
                    cache.replaceCacheBlock(tag, index);
                } else {
                    //否则命中数累计
                    read_data_hit++;
                }
            }
            //写数据
            else if (ins_type == 1) {
                hitornot = cache.write(tag, index);
                //不命中，记录miss数
                if (!hitornot) {
                    write_data_miss++;
                    //按写分配
                    if (allocIndex == 0) {
                        cache.replaceCacheBlock(tag, index);
                        cache.write(tag, index);
                    } else {
                        cache.writeToMemory();
                    }
                } else {
                    write_data_hit++;
                }
            } else {
                hitornot = cache.read(tag, index);
                if (!hitornot) {
                    read_ins_miss++;
                    cache.replaceCacheBlock(tag, index);
                    if (prefetchIndex == 1)
                        cache.prefetch(ins);
                } else {
                    read_ins_hit++;
                }
            }
        } else {
            if (ins_type == 0) {
                tag = ins.getTag(dataCache.getGroupNum(), dataCache.getBlockSize());
                index = ins.getIndex(dataCache.getGroupNum(), dataCache.getBlockSize());
                hitornot = dataCache.read(tag, index);
                if (!hitornot) {
                    read_data_miss++;
                    dataCache.replaceCacheBlock(tag, index);
                } else {
                    read_data_hit++;
                }
            } else if (ins_type == 1) {
                tag = ins.getTag(dataCache.getGroupNum(), dataCache.getBlockSize());
                index = ins.getIndex(dataCache.getGroupNum(), dataCache.getBlockSize());
                hitornot = dataCache.write(tag, index);
                if (!hitornot) {
                    write_data_miss++;
                    if (allocIndex == 0) {
                        dataCache.replaceCacheBlock(tag, index);
                        dataCache.write(tag, index);
                    } else {
                        dataCache.writeToMemory();
                    }
                } else {
                    write_data_hit++;
                }
            } else {
                tag = ins.getTag(insCache.getGroupNum(), insCache.getBlockSize());
                index = ins.getIndex(insCache.getGroupNum(), insCache.getBlockSize());
                hitornot = insCache.read(tag, index);
                if (!hitornot) {
                    read_ins_miss++;
                    insCache.replaceCacheBlock(tag, index);
                    if (prefetchIndex == 1)
                        insCache.prefetch(ins);
                } else {
                    read_ins_hit++;
                }
            }
        }

        if (oneStep || ip == ins_list.size() - 1)
            updateUI(ins, hitornot, oneStep);

        ++ip;
    }

    /**
     * 模拟执行到底
     */
    private void simExecAll() {
        while (ip < ins_list.size()) {
            simExecStep(false);
        }
        ip = 0;
        initCache();
    }

    /**
     * 全部复位
     */
    private void resetAll() {
        csBox.setEnabled(true);
        bsBox.setEnabled(true);
        wayBox.setEnabled(true);
        replaceBox.setEnabled(true);
        prefetchBox.setEnabled(true);
        writeBox.setEnabled(true);
        allocBox.setEnabled(true);
        fileError.setVisible(false);
        icsBox.setSelectedIndex(0);
        dcsBox.setSelectedIndex(0);
        csBox.setSelectedIndex(0);
        bsBox.setSelectedIndex(0);
        wayBox.setSelectedIndex(0);
        replaceBox.setSelectedIndex(0);
        prefetchBox.setSelectedIndex(0);
        writeBox.setSelectedIndex(0);
        allocBox.setSelectedIndex(0);
        fileAddrBtn.setText(null);
        execAllBtn.setEnabled(false);
        execStepBtn.setEnabled(false);
        icsBox.setEnabled(false);
        dcsBox.setEnabled(false);
        csBox.setEnabled(true);
        unifiedCacheButton.setEnabled(true);
        separateCacheButton.setEnabled(true);
        unifiedCacheButton.setSelected(true);
        separateCacheButton.setSelected(false);
        cacheType = 0;
        icsIndex = 0;
        dcsIndex = 0;
        csIndex = 0;
        bsIndex = 0;
        wayIndex = 0;
        replaceIndex = 0;
        prefetchIndex = 0;
        writeIndex = 0;
        allocIndex = 0;
        ins_list.clear();
        ip = 0;
        clearUI();
    }

    /**
     * 绘制 Cache 模拟器图形化界面
     * 无需做修改
     */
    private void draw() {
        //模拟器绘制面板
        setLayout(new BorderLayout(5, 5));
        JPanel panelTop = new JPanel();
        JPanel panelLeft = new JPanel();
        JPanel panelRight = new JPanel();
        JPanel panelBottom = new JPanel();
        panelTop.setPreferredSize(new Dimension(800, 30));
        panelLeft.setPreferredSize(new Dimension(300, 500));
        panelRight.setPreferredSize(new Dimension(500, 500));
        panelBottom.setPreferredSize(new Dimension(800, 80));
        panelTop.setBorder(new EtchedBorder(EtchedBorder.RAISED));
        panelLeft.setBorder(new EtchedBorder(EtchedBorder.RAISED));
        panelRight.setBorder(new EtchedBorder(EtchedBorder.RAISED));
        panelBottom.setBorder(new EtchedBorder(EtchedBorder.RAISED));

        //*****************************顶部面板绘制*****************************************//
        labelTop = new JLabel("Cache Simulator");
        labelTop.setAlignmentX(CENTER_ALIGNMENT);
        panelTop.add(labelTop);


        //*****************************左侧面板绘制*****************************************//
        labelLeft = new JLabel("Cache 参数设置");
        labelLeft.setPreferredSize(new Dimension(150, 40));
        resetBotton = new JButton("复位");
        resetBotton.setPreferredSize(new Dimension(70, 30));
        resetBotton.addActionListener(this);

        //cache 大小设置
        unifiedCacheButton = new JRadioButton("统一Cache:", true);
        unifiedCacheButton.setPreferredSize(new Dimension(120, 30));
        unifiedCacheButton.addActionListener(e -> {
            icsBox.setEnabled(false);
            dcsBox.setEnabled(false);
            csBox.setEnabled(true);
            separateCacheButton.setSelected(false);
            cacheType = 0;
        });

        separateCacheButton = new JRadioButton("独立Cache:");
        separateCacheButton.setPreferredSize(new Dimension(100, 30));
        separateCacheButton.addActionListener(e -> {
            icsBox.setEnabled(true);
            dcsBox.setEnabled(true);
            csBox.setEnabled(false);
            unifiedCacheButton.setSelected(false);
            cacheType = 1;
        });

        csBox.setPreferredSize(new Dimension(160, 30));
        csBox.addItemListener(e -> csIndex = csBox.getSelectedIndex());

        icsLabel = new JLabel("指令");
        icsLabel.setPreferredSize(new Dimension(50, 30));

        dcsLabel = new JLabel("数据");
        dcsLabel.setPreferredSize(new Dimension(50, 30));

        icsBox.setPreferredSize(new Dimension(120, 30));
        icsBox.addItemListener(e -> icsIndex = icsBox.getSelectedIndex());
        icsBox.setEnabled(false);
        dcsBox.setPreferredSize(new Dimension(120, 30));
        dcsBox.addItemListener(e -> dcsIndex = dcsBox.getSelectedIndex());
        dcsBox.setEnabled(false);

        JLabel emptyLabel = new JLabel();
        emptyLabel.setPreferredSize(new Dimension(100, 30));

        //cache 块大小设置
        bsLabel = new JLabel("块大小");
        bsLabel.setPreferredSize(new Dimension(120, 30));
        bsBox.setPreferredSize(new Dimension(160, 30));
        bsBox.addItemListener(e -> bsIndex = bsBox.getSelectedIndex());

        //相连度设置
        wayLabel = new JLabel("相联度");
        wayLabel.setPreferredSize(new Dimension(120, 30));
        wayBox.setPreferredSize(new Dimension(160, 30));
        wayBox.addItemListener(e -> wayIndex = wayBox.getSelectedIndex());

        //替换策略设置
        replaceLabel = new JLabel("替换策略");
        replaceLabel.setPreferredSize(new Dimension(120, 30));
        replaceBox.setPreferredSize(new Dimension(160, 30));
        replaceBox.addItemListener(e -> replaceIndex = replaceBox.getSelectedIndex());

        //欲取策略设置
        prefetchLabel = new JLabel("预取策略");
        prefetchLabel.setPreferredSize(new Dimension(120, 30));
        prefetchBox.setPreferredSize(new Dimension(160, 30));
        prefetchBox.addItemListener(e -> prefetchIndex = prefetchBox.getSelectedIndex());

        //写策略设置
        writeLabel = new JLabel("写策略");
        writeLabel.setPreferredSize(new Dimension(120, 30));
        writeBox.setPreferredSize(new Dimension(160, 30));
        writeBox.addItemListener(e -> writeIndex = writeBox.getSelectedIndex());

        //调块策略
        allocLabel = new JLabel("写不命中调块策略");
        allocLabel.setPreferredSize(new Dimension(120, 30));
        allocBox.setPreferredSize(new Dimension(160, 30));
        allocBox.addItemListener(e -> allocIndex = allocBox.getSelectedIndex());

        //选择指令流文件
        fileLabel = new JLabel("选择指令流文件");
        fileLabel.setPreferredSize(new Dimension(120, 30));
        fileAddrBtn = new JLabel();
        fileAddrBtn.setPreferredSize(new Dimension(210, 30));
        fileAddrBtn.setBorder(new EtchedBorder(EtchedBorder.RAISED));
        fileBotton = new JButton("浏览");
        fileBotton.setPreferredSize(new Dimension(70, 30));
        fileBotton.addActionListener(this);
        fileError = new JLabel("载入文件类型错误！");
        fileError.setPreferredSize(new Dimension(150, 30));
        fileError.setVisible(false);
        fileError.setForeground(Color.red);

        panelLeft.add(labelLeft);
        panelLeft.add(resetBotton);
        panelLeft.add(unifiedCacheButton);
        panelLeft.add(csBox);
        panelLeft.add(separateCacheButton);
        panelLeft.add(icsLabel);
        panelLeft.add(icsBox);
        panelLeft.add(emptyLabel);
        panelLeft.add(dcsLabel);
        panelLeft.add(dcsBox);
        panelLeft.add(bsLabel);
        panelLeft.add(bsBox);
        panelLeft.add(wayLabel);
        panelLeft.add(wayBox);
        panelLeft.add(replaceLabel);
        panelLeft.add(replaceBox);
        panelLeft.add(prefetchLabel);
        panelLeft.add(prefetchBox);
        panelLeft.add(writeLabel);
        panelLeft.add(writeBox);
        panelLeft.add(allocLabel);
        panelLeft.add(allocBox);
        panelLeft.add(fileLabel);
        panelLeft.add(fileAddrBtn);
        panelLeft.add(fileBotton);
        panelLeft.add(fileError);

        //*****************************右侧面板绘制*****************************************//
        //模拟结果展示区域
        rightLabel = new JLabel("模拟结果", JLabel.CENTER);
        rightLabel.setPreferredSize(new Dimension(500, 40));
        panelRight.add(rightLabel);

        JLabel[][] resultsTag = new JLabel[4][3];
        resultsData = new JLabel[4][3];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                if (j == 1) {
                    resultsTag[i][j] = new JLabel("不命中次数");
                } else if (j == 2) {
                    resultsTag[i][j] = new JLabel("不命中率");
                } else if (i == 0) {
                    resultsTag[i][j] = new JLabel("访问总次数");
                } else if (i == 1) {
                    resultsTag[i][j] = new JLabel("读指令次数");
                } else if (i == 2) {
                    resultsTag[i][j] = new JLabel("读数据次数");
                } else if (i == 3) {
                    resultsTag[i][j] = new JLabel("写数据次数");
                }
                resultsTag[i][j].setPreferredSize(new Dimension(70, 40));
                if (j != 2) {
                    resultsData[i][j] = new JLabel("0", JLabel.CENTER);
                } else {
                    resultsData[i][j] = new JLabel("0.00%", JLabel.CENTER);
                }
                resultsData[i][j].setPreferredSize(new Dimension(80, 40));
                panelRight.add(resultsTag[i][j]);
                panelRight.add(resultsData[i][j]);
            }

        }

        stepLabel = new JLabel[6];
        stepContent = new JLabel[6];
        stepLabel[0] = new JLabel("访问类型：", JLabel.RIGHT);
        stepLabel[1] = new JLabel("地址：", JLabel.RIGHT);
        stepLabel[2] = new JLabel("块号：", JLabel.RIGHT);
        stepLabel[3] = new JLabel("块内地址：", JLabel.RIGHT);
        stepLabel[4] = new JLabel("索引：", JLabel.RIGHT);
        stepLabel[5] = new JLabel("命中情况：", JLabel.RIGHT);
        for (int i = 0; i < 6; ++i) {
            stepLabel[i].setVisible(false);
            stepLabel[i].setPreferredSize(new Dimension(200, 30));
            panelRight.add(stepLabel[i]);
            stepContent[i] = new JLabel("???", JLabel.LEFT);
            stepContent[i].setForeground(Color.red);
            stepContent[i].setVisible(false);
            stepContent[i].setPreferredSize(new Dimension(200, 30));
            panelRight.add(stepContent[i]);
        }

        //*****************************底部面板绘制*****************************************//

        bottomLabel = new JLabel("执行控制");
        bottomLabel.setPreferredSize(new Dimension(800, 30));
        execStepBtn = new JButton("步进");
        execStepBtn.setLocation(100, 30);
        execStepBtn.addActionListener(this);
        execStepBtn.setEnabled(false);
        execAllBtn = new JButton("执行到底");
        execAllBtn.setLocation(300, 30);
        execAllBtn.addActionListener(this);
        execAllBtn.setEnabled(false);

        panelBottom.add(bottomLabel);
        panelBottom.add(execStepBtn);
        panelBottom.add(execAllBtn);

        setResizable(false);
        add("North", panelTop);
        add("West", panelLeft);
        add("Center", panelRight);
        add("South", panelBottom);
        setSize(820, 620);
        setVisible(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    private void clearUI() {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 2; j++) {
                resultsData[i][j].setText("0");
            }
            resultsData[i][2].setText("0.00%");
        }
        for (int i = 0; i < 6; i++) {
            stepContent[i].setVisible(false);
            stepLabel[i].setVisible(false);
            stepContent[i].setText("???");
        }
        read_data_hit = read_ins_hit = write_data_hit = 0;
        read_data_miss = read_ins_miss = write_data_miss = 0;
    }

    private void updateUI(Instruction ins, Boolean hitornot, Boolean oneStep) {
        int total_miss = read_data_miss + read_ins_miss + write_data_miss;
        int total_hit = read_data_hit + read_ins_hit + write_data_hit;
        double missRate;

        resultsData[0][0].setText((total_hit + total_miss) + "");
        resultsData[0][1].setText(total_miss + "");
        if (total_hit + total_miss > 0) {
            missRate = ((double) (total_miss) / (double) (total_miss + total_hit)) * 100;
            resultsData[0][2].setText(String.format("%.2f", missRate) + "%");
        }

        resultsData[1][0].setText((read_ins_hit + read_ins_miss) + "");
        resultsData[1][1].setText(read_ins_miss + "");
        if (read_ins_miss + read_ins_hit > 0) {
            missRate = ((double) (read_ins_miss) / (double) (read_ins_miss + read_ins_hit)) * 100;
            resultsData[1][2].setText(String.format("%.2f", missRate) + "%");
        }

        resultsData[2][0].setText((read_data_miss + read_data_hit) + "");
        resultsData[2][1].setText(read_data_miss + "");
        if (read_data_hit + read_data_miss > 0) {
            missRate = ((double) (read_data_miss) / (double) (read_data_hit + read_data_miss)) * 100;
            resultsData[2][2].setText(String.format("%.2f", missRate) + "%");
        }

        resultsData[3][0].setText((write_data_hit + write_data_miss) + "");
        resultsData[3][1].setText(write_data_miss + "");
        if (write_data_hit + write_data_miss > 0) {
            missRate = ((double) (write_data_miss) / (double) (write_data_miss + write_data_hit)) * 100;
            resultsData[3][2].setText(String.format("%.2f", missRate) + "%");
        }

        if (oneStep) {
            if (hitornot) {
                stepContent[5].setText("命中");
            } else {
                stepContent[5].setText("不命中");
            }

            int ins_type = ins.getInsType();
            if (ins_type == 0) {
                stepContent[0].setText("读指令");
            } else if (ins_type == 1) {
                stepContent[0].setText("读数据");
            } else {
                stepContent[0].setText("写数据");
            }

            stepContent[1].setText(ins.getAddr().toString());
            stepContent[2].setText(ins.getBlock_offset().toString());
            stepContent[3].setText(ins.getIns_offset().toString());
            stepContent[4].setText(ins.getIndex().toString());
            for (int i = 0; i < 6; i++) {
                stepContent[i].setVisible(true);
                stepLabel[i].setVisible(true);
            }
        } else {
            for (int i = 0; i < 6; i++) {
                stepContent[i].setVisible(false);
                stepLabel[i].setVisible(false);
            }
        }
    }

    public static void main(String[] args) {
        new CCacheSim();
    }
}

class Cache {

    //groupNum是cache内的组数，wayNum是组相连的路数
    private final int blockSize, wayNum, groupNum, replaceType, writeType;

    private class CacheBlock {
        private int tag;    //tag表示此块是否可用, false表示不可用
        private boolean dirty;  //dirty位
        private int time;       //FIFO置换策略用到的时间
        private int count;      //LRU置换策略用到的计数

        CacheBlock(int tag) {
            this.tag = tag;
            this.time = -1;
            this.count = -1;
        }
    }

    private CacheBlock cacheArray[][];
    private int fifo_order[];
    private int lru_order[];

    Cache(int cacheSize, int blockSize, int wayNum, int replaceType, int writeType) {
        this.blockSize = blockSize;
        this.wayNum = wayNum;
        this.replaceType = replaceType;
        this.writeType = writeType;
        this.groupNum = cacheSize / (blockSize * wayNum);
        cacheArray = new CacheBlock[groupNum][wayNum];
        fifo_order = new int[groupNum];
        lru_order = new int[groupNum];
        for (int i = 0; i < groupNum; ++i) {
            for (int j = 0; j < wayNum; ++j) {
                cacheArray[i][j] = new CacheBlock(-1);
            }
        }
    }

    int getGroupNum() {
        return groupNum;
    }

    int getBlockSize() {
        return blockSize;
    }

    boolean read(int tag, int index) {
        for (int i = 0; i < wayNum; ++i) {
            if (cacheArray[index][i].tag == tag) {
                cacheArray[index][i].count = lru_order[index];
                lru_order[index]++;
                return true;
            }
        }
        return false;
    }

    boolean write(int tag, int index) {
        for (int i = 0; i < wayNum; i++)
            if (cacheArray[index][i].tag == tag) {
                cacheArray[index][i].count = lru_order[index];
                lru_order[index]++;
                cacheArray[index][i].dirty = true;
                //如果是写直达法, 直接写入内存, dirty置为false
                if (writeType == 1) {
                    cacheArray[index][i].dirty = false;
                    writeToMemory();
                }
                return true;
            }
        return false;
    }

    void prefetch(Instruction ins) {
        int nextTag = (ins.getBlock_offset() + 1) / groupNum;
        int nextIndex = (ins.getBlock_offset() + 1) % groupNum;
        replaceCacheBlock(nextTag, nextIndex);
    }

    void replaceCacheBlock(int tag, int index) {
        if (replaceType == 0) {         //LRU
            int lruBlock = 0;
            for (int i = 0; i < wayNum; ++i) {
                if (cacheArray[index][lruBlock].count > cacheArray[index][i].count)
                    lruBlock = i;
            }
            loadToCache(tag, index, lruBlock);
        } else if (replaceType == 1) {     //FIFO
            int fifoBlock = 0;
            for (int i = 1; i < wayNum; ++i) {
                if (cacheArray[index][fifoBlock].time > cacheArray[index][i].time)
                    fifoBlock = i;
            }
            loadToCache(tag, index, fifoBlock);
        } else {
            loadToCache(tag, index, (int) (Math.random() * wayNum));
        }
    }

    private void loadToCache(int tag, int index, int i) {
        if (writeType == 0 && cacheArray[index][i].dirty) {
            writeToMemory();
        }
        cacheArray[index][i].tag = tag;
        cacheArray[index][i].count = lru_order[index];
        lru_order[index]++;
        cacheArray[index][i].dirty = false;
        cacheArray[index][i].time = fifo_order[index];
        fifo_order[index]++;
    }

    void writeToMemory() {

    }
}

class Instruction {
    private final Integer ins_type; //指令类型
    private final Integer ins_addr; //整型指令地址
    private Integer tag;//tag表示组内分配的块号
    private Integer index;//index表示该指令地址对应的组号
    private Integer ins_offset;
    private Integer block_offset;

    Integer getInsType() {
        return ins_type;
    }

    int getTag(int groupNum, int blockSize) {
        this.tag = ins_addr / (blockSize * groupNum);
        this.ins_offset = ins_addr % blockSize;
        this.block_offset = ins_addr / blockSize;
        return tag;
    }

    Integer getAddr() {
        return ins_addr;
    }

    Integer getBlock_offset() {
        return block_offset;
    }

    Integer getIndex() {
        return index;
    }

    int getIndex(int groupNum, int blockSize) {
        this.index = (ins_addr / blockSize) % groupNum;
        this.ins_offset = ins_addr % blockSize;
        this.block_offset = ins_addr / blockSize;
        return index;
    }

    Integer getIns_offset() {
        return ins_offset;
    }

    Instruction(Integer ins_type, String ins_addr_hex) {
        this.ins_type = ins_type;
        this.ins_addr = Integer.parseInt(ins_addr_hex, 16);
    }
}