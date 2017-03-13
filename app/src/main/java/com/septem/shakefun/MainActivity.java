package com.septem.shakefun;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.*;
import com.avos.avoscloud.*;
import com.avos.avoscloud.feedback.FeedbackAgent;
import com.dd.CircularProgressButton;
import me.drakeet.materialdialog.MaterialDialog;

import java.util.*;

public class MainActivity extends AppCompatActivity implements SensorEventListener
{
	private int totalShake;
	private int hasShake;
	private int progress;
	private int bestScore;
	private int readyTime = 2;
	private String tempNicName;
	private String nicName;
	private Map<String,Integer> dataMap;

//	private MediaPlayer player;

	private SharedPreferences pref;
	private SharedPreferences.Editor editor;

	private SensorManager sensorManager;

	private CircularProgressButton startButton;
	private CircularProgressButton rankButton;
	private CircularProgressButton exitButton;
	private CircularProgressButton feedbackButton;
	private ImageView shakeImage;
	private TextView notes;
	private TextView readyText;
	private RunningTextView score;
	private TextView levelText;
	private MaterialDialog nameEditorDialog;
	private EditText nicNameEdit;
	private MaterialDialog rankDialog;
	private View rankLayout;
	private ListView scoreList;

	private Timer timerForGame;
	private Timer timerForReady;

	private Animation appearAnimation = new AlphaAnimation(0.1f, 1.0f);
	private Animation fadeAnimation = new AlphaAnimation(1.0f,0.1f);

	private Handler handler = new Handler() //子线程通知主线程更新UI
	{
		@Override
		public void handleMessage(Message msg)
		{
			updateUI(msg);
		}
	};

	private AVQuery<AVObject> queryForPutName;
	private AVQuery<AVObject> queryForPutScore;
	private AVQuery<AVObject> queryForGetScore;
	private FeedbackAgent agent;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_main);
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		connectToAVOSClound();
		loadLocalData();
		initView();
	}



	private void connectToAVOSClound()
	{
		AVOSCloud.initialize(this, "CHOMiKFotwNvLM7Rx5WGBcfu-gzGzoHsz", "V2R6QtWq1TPzwNaxa8LnmDAe");
		AVAnalytics.trackAppOpened(getIntent());
		queryForPutName = new AVQuery<>("gameScoreList");
		queryForPutScore = new AVQuery<>("gameScoreList");
		queryForGetScore = new AVQuery<>("gameScoreList");
		dataMap = new HashMap<>();
		agent = new FeedbackAgent(this);
		agent.sync();
	}


	private void initView()
	{

		appearAnimation.setDuration(250);
		fadeAnimation.setDuration(250);

		LayoutInflater inflater = LayoutInflater.from(this);
		rankLayout = inflater.inflate(R.layout.layout_rank, null);
		rankLayout.setMinimumHeight(500);
		scoreList = (ListView) rankLayout.findViewById(R.id.listView_score);
		startButton = (CircularProgressButton) findViewById(R.id.btn_start);
		rankButton = (CircularProgressButton) findViewById(R.id.btn_rank);
		exitButton = (CircularProgressButton) findViewById(R.id.btn_exit);
		feedbackButton = (CircularProgressButton) findViewById(R.id.btn_feedback);
		shakeImage = (ImageView) findViewById(R.id.imageShake);
		notes = (TextView) findViewById(R.id.tips);
		score = (RunningTextView) findViewById(R.id.score);
		levelText = (TextView) findViewById(R.id.level);
		readyText = (TextView) findViewById(R.id.text_ready);
		rankDialog = new MaterialDialog(this)
				.setContentView(rankLayout)
				.setPositiveButton("朕知道了", new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						rankDialog.dismiss();
					}
				});

		startButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if(nicName.equals("null"))
				{
					nameEditorDialog = new MaterialDialog(MainActivity.this)
							.setTitle("Have fun!")
							.setContentView(R.layout.dialog_editor)
							.setPositiveButton("OK", new View.OnClickListener() {
								@Override
								public void onClick(View v)
								{
									nicNameEdit = (EditText) v.getRootView().findViewById(R.id.edit_name);
									tempNicName = nicNameEdit.getText().toString();
									queryForPutName.whereEqualTo("name", tempNicName);
									queryForPutName.findInBackground(new FindCallback<AVObject>()
									{
										public void done(List<AVObject> avObjects, AVException e)
										{
											if (e == null)
											{
												if (avObjects.size() != 0 || tempNicName.equals("null") || tempNicName.length() == 0)
												{
													Toast.makeText(getApplicationContext(), "该昵称非法或已经被占用，换一个吧", Toast.LENGTH_SHORT).show();
												}
												else
												{
													nicName = tempNicName;
													editor.putString("name_player", nicName).commit();
													Toast.makeText(getApplicationContext(), "昵称设置成功，可以开始游戏啦", Toast.LENGTH_SHORT).show();
													nameEditorDialog.dismiss();
												}
											}
											else
											{
												if (e.getMessage().equals("java.net.UnknownHostException"))
													Toast.makeText(getApplicationContext(), "网络异常，等下再试试看吧", Toast.LENGTH_SHORT).show();
												else
													Toast.makeText(getApplicationContext(), "未知异常：" + e.getMessage(), Toast.LENGTH_SHORT).show();
											}
										}
									});
								}
							})
							.setNegativeButton("取消", new View.OnClickListener() {
								@Override
								public void onClick(View v)
								{
									nameEditorDialog.dismiss();
								}
							});
					nameEditorDialog.show();
				}

				if (progress == 0&&startButton.getProgress()==0&&!nicName.equals("null"))
				{
					timerForReady = new Timer();
					timerForReady.schedule(new TimerTask() {
						@Override
						public void run()
						{
							handler.sendEmptyMessage(0);
							if (readyTime==-1)
							{
								timerForReady.cancel();
								timerForGame = new Timer();
								timerForGame.schedule(new TimerTask()
								{
									@Override
									public void run()
									{
										if (progress == 0)
											onGameStar();
										handler.sendEmptyMessage(1);
										if (progress >= 100)
										{
											onGameStop();
											timerForGame.cancel();
										} else
											++progress;
									}
								}, 0, 100);
							}
						}
					},0,1000);
				}
			}
		});

		rankButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if(rankButton.getProgress()==0)
				{
					queryForGetScore.whereNotEqualTo("name", "null");
					queryForGetScore.findInBackground(new FindCallback<AVObject>()
					{
						@Override
						public void done(List<AVObject> list, AVException e)
						{
							if (e==null)
							{
								for (AVObject avObject : list)
									dataMap.put(avObject.getString("name"), avObject.getInt("score"));
								SimpleAdapter adapter = new SimpleAdapter(MainActivity.this, getData(),
										R.layout.item_rank,
										new String[] { "name", "score" },
										new int[] {R.id.rank_name, R.id.rank_score });
								scoreList.setAdapter(adapter);
								rankDialog.show();
							}
							else
							{
								if (e.getMessage().equals("java.net.UnknownHostException"))
									Toast.makeText(getApplicationContext(), "网络异常，等下再试试看吧", Toast.LENGTH_SHORT).show();
								else
									Toast.makeText(getApplicationContext(), "未知异常：" + e.getMessage(), Toast.LENGTH_SHORT).show();
								rankButton.setProgress(1);
								rankButton.setProgress(-1);
							}
						}
					});
				}
				else if(rankButton.getProgress()==-1)
					rankButton.setProgress(0);
			}
		});

		feedbackButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v)
			{
				agent.startDefaultThreadActivity();
			}
		});

		exitButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				finish();
			}
		});
	}



	private List<Map<String, Object>> getData()   //将数据添加到适配器
	{
		Map sortedMap = sortMapByValue(dataMap);
		List<Map<String, Object>> list = new ArrayList<>();
		for (Object o : sortedMap.entrySet())
		{
			Map.Entry entry = (Map.Entry) o;
			Map<String, Object> map = new HashMap<>();
			map.put("name",entry.getKey());
			map.put("score",entry.getValue());
			list.add(map);
		}
		return list;
	}

	private Map<String, Integer> sortMapByValue(Map<String, Integer> oriMap)  //将分数排序
	{
		Map<String, Integer> sortedMap = new LinkedHashMap<>();
		List<Map.Entry<String, Integer>> entryList = new ArrayList<>(oriMap.entrySet());
		Collections.sort(entryList, new Comparator<Map.Entry<String, Integer>>()
		{
			@Override
			public int compare(Map.Entry<String, Integer> lhs, Map.Entry<String, Integer> rhs)
			{
				return rhs.getValue().compareTo(lhs.getValue());
			}
		});
		Iterator<Map.Entry<String, Integer>> iter = entryList.iterator();
		Map.Entry<String, Integer> tmpEntry;
		while (iter.hasNext())
		{
			tmpEntry = iter.next();
			sortedMap.put(tmpEntry.getKey(), tmpEntry.getValue());
		}
		return sortedMap;
	}


	private void loadLocalData()
	{
		pref = getSharedPreferences("gameData",MODE_PRIVATE);
		editor = pref.edit();
		bestScore = pref.getInt("score_best", 0);      //从本地首选项中获取数据
		nicName = pref.getString("name_player","null");
	}

	private void onGameStar()
	{
		totalShake=0;             //重置数据和注册传感器
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_FASTEST);
		handler.sendEmptyMessage(3);
	}

	private void onGameStop()
	{
		if(totalShake > bestScore)                   //更新最高记录到本地
		{
			bestScore = totalShake;
			editor.putInt("score_best", bestScore).commit();
		}
		((Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(750);
		progress = 0;
		readyTime = 2;
		sensorManager.unregisterListener(this);
		handler.sendEmptyMessage(2);
		queryForPutScore.whereEqualTo("name", nicName);
		queryForPutScore.findInBackground(new FindCallback<AVObject>()   //更新最高纪录到服务器
		{
			@Override
			public void done(List<AVObject> list, AVException e)
			{
				if (e == null)
				{
					if (list.size() == 0)
					{
						AVObject post = new AVObject("gameScoreList");
						post.put("name", nicName);
						post.put("score", bestScore);
						post.saveInBackground();
					} else
					{
						AVObject update = AVObject.createWithoutData("gameScoreList", list.get(0).getObjectId());
						update.put("score", bestScore);
						update.saveInBackground();
					}
				}
			}
		});
	}

	private void setLevel()
	{
		if(totalShake<20)
		{
	//		player = MediaPlayer.create(getApplicationContext(),R.raw.level1);
			levelText.setText(R.string.level1);
		}
		else if (totalShake>=20&&totalShake<45)
		{
	//		player = MediaPlayer.create(getApplicationContext(),R.raw.level2);
			levelText.setText(R.string.level2);
		}
		else if(totalShake>=45)
		{
	//		player = MediaPlayer.create(getApplicationContext(),R.raw.level3);
			levelText.setText(R.string.level3);
		}
	}

	private void updateUI(Message msg)
	{
		switch (msg.what)
		{
			case 0:       //游戏准备时的UI更新
				switch (readyTime)
				{
					case 2:
						readyText.setVisibility(View.VISIBLE);
						exitButton.setVisibility(View.INVISIBLE);
						if(rankButton.getVisibility()==View.VISIBLE)
						{
							rankButton.startAnimation(fadeAnimation);
							exitButton.startAnimation(fadeAnimation);
							feedbackButton.startAnimation(fadeAnimation);
							startButton.startAnimation(fadeAnimation);
						}
						else
						{
							startButton.startAnimation(fadeAnimation);
							exitButton.startAnimation(fadeAnimation);
						}
						startButton.setVisibility(View.INVISIBLE);
						rankButton.setVisibility(View.INVISIBLE);
						exitButton.setVisibility(View.INVISIBLE);
						feedbackButton.setVisibility(View.INVISIBLE);
						readyText.setTextSize(100);
						readyText.setText(""+readyTime);
						break;
					case 1:
						readyText.setText(""+readyTime);
						break;
					case 0:
						readyText.setTextSize(50);
						readyText.setText("游戏开始！");
						break;
				}
				--readyTime;
				break;
			case 1:    //游戏进行时的UI更新
				startButton.setProgress(progress);
				if(startButton.getProgress()%2==0)
					shakeImage.scrollTo(0,15); //向上移动
				else
					shakeImage.scrollTo(0,-15);  //向下移动
				break;
			case 2:     //游戏结束时的UI更新
				setLevel();
				shakeImage.startAnimation(fadeAnimation);
				shakeImage.setVisibility(View.INVISIBLE);
				score.setVisibility(View.VISIBLE);
				score.startAnimation(appearAnimation);
				startButton.setProgress(100);
				score.withNumber(totalShake);
				score.setDuration(2000);
				score.startRunning();
				score.setOnEndListener(new RunningTextView.EndListener()
				{
					@Override
					public void onEndFinish()
					{         //分数跑完后的UI更新
						startButton.setProgress(0);
						startButton.setIdleText("再来一局");
						notes.setText("最高纪录:" + bestScore);
						rankButton.setVisibility(View.VISIBLE);
						exitButton.setVisibility(View.VISIBLE);
						feedbackButton.setVisibility(View.VISIBLE);
						notes.setVisibility(View.VISIBLE);
						levelText.setVisibility(View.VISIBLE);
						rankButton.startAnimation(appearAnimation);
						exitButton.startAnimation(appearAnimation);
						feedbackButton.startAnimation(appearAnimation);
						notes.startAnimation(appearAnimation);
						levelText.startAnimation(appearAnimation);
						//player.start();
					}
				});
				break;
			case 3:        //游戏开始时的UI更新
				readyText.setVisibility(View.INVISIBLE);
				startButton.setVisibility(View.VISIBLE);
				levelText.setVisibility(View.INVISIBLE);
				notes.setVisibility(View.INVISIBLE);
				score.setVisibility(View.INVISIBLE);
				shakeImage.setVisibility(View.VISIBLE);

				break;
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		//屏蔽返回键
		return keyCode == KeyEvent.KEYCODE_BACK || super.onKeyDown(keyCode, event);
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		float[] values = event.values;
		if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
		{
			if ((values[1] >= 5.0f) && (hasShake == 0))  //向上摇
				hasShake = 1;
			if ((values[1] <= -5.0f) && (hasShake == 1))  //向下摇
				hasShake = 2;
			if ((values[1] >= 5.0f) && (hasShake == 2))
				hasShake = 3;
			if ((values[1] <= 5.0f) && (hasShake == 3))
				hasShake = 4;
			if (hasShake == 4)            //因机型差异，为了保证数据精准取值4次
			{
				hasShake = 0;
				totalShake++;
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{

	}
}
