package com.xiaofa.flutter_lc_im;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.leancloud.AVFile;
import cn.leancloud.AVInstallation;
import cn.leancloud.AVLogger;
import cn.leancloud.AVOSCloud;
import cn.leancloud.AVObject;
import cn.leancloud.AVQuery;
import cn.leancloud.im.AVIMOptions;
import cn.leancloud.im.v2.AVIMClient;
import cn.leancloud.im.v2.AVIMConversation;
import cn.leancloud.im.v2.AVIMConversationsQuery;
import cn.leancloud.im.v2.AVIMException;
import cn.leancloud.im.v2.AVIMMessage;
import cn.leancloud.im.v2.AVIMMessageManager;
import cn.leancloud.im.v2.AVIMMessageOption;
import cn.leancloud.im.v2.annotation.AVIMMessageType;
import cn.leancloud.im.v2.callback.AVIMClientCallback;
import cn.leancloud.im.v2.callback.AVIMConversationCallback;
import cn.leancloud.im.v2.callback.AVIMConversationCreatedCallback;
import cn.leancloud.im.v2.callback.AVIMConversationQueryCallback;
import cn.leancloud.im.v2.callback.AVIMMessagesQueryCallback;
import cn.leancloud.im.v2.messages.AVIMAudioMessage;
import cn.leancloud.im.v2.messages.AVIMImageMessage;
import cn.leancloud.im.v2.messages.AVIMTextMessage;
import cn.leancloud.im.v2.messages.AVIMVideoMessage;
import cn.leancloud.push.PushService;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/** FlutterLcImPlugin */
public class FlutterLcImPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler {

  public static final String FLUTTER_IM_NAME = "flutter_lc_im";
  public static final String FLUTTER_CHANNEL_MESSAGE = "flutter_lc_im/messages";
  public static final String FLUTTER_CHANNEL_CONVERSATION = "flutter_lc_im/conversations";
  public static final String FLUTTER_CHANNEL_NOTIFICATION = "flutter_lc_im/notifications";

  static boolean isRegister = false;

  private AVIMClient client;
  private AVIMConversation conversation;

  static Context context;
  static Activity activity;
  static BinaryMessenger messenger;

  private EventChannel.EventSink conversationEventCallback;
  private EventChannel.EventSink messageEventCallback;

  private LCConversationEventHandler conversationEventHandler;
  private LCMessageHandler messageHandler;

  //留给客户端做数据交互
  public static EventChannel.EventSink notificationEventCallback;

  public FlutterLcImPlugin() {
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    context = flutterPluginBinding.getApplicationContext();
    messenger = flutterPluginBinding.getBinaryMessenger();
    final MethodChannel channel = new MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), FLUTTER_IM_NAME);
    channel.setMethodCallHandler(new FlutterLcImPlugin());
  }


  // This static function is optional and equivalent to onAttachedToEngine. It supports the old
  // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
  // plugin registration via this function while apps migrate to use the new Android APIs
  // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
  //
  // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
  // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
  // depending on the user's project. onAttachedToEngine or registerWith must both be defined
  // in the same class.
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), FLUTTER_IM_NAME);
    FlutterLcImPlugin im = new FlutterLcImPlugin();
    context = registrar.context();
    activity = registrar.activity();
    messenger = registrar.messenger();
    channel.setMethodCallHandler(im);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    activity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {

  }

  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {

  }

  @Override
  public void onDetachedFromActivity() {

  }


  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {

    switch (call.method) {
      case "register":
        if (!isRegister) {

          String appId = call.argument("app_id");
          String appKey = call.argument("app_key");
          String api = call.argument("api");
          boolean debug = call.argument("debug");

          this.initSetting(appId, appKey, api, debug);
          isRegister = true;

        } else {

          result.success("success");

        }
        break;
      case "login":

        String clientId = call.argument("client_id");
        this.login(clientId, result);
        break;

      case "createConversation":

        clientId = call.argument("client_id");
        String peerId = call.argument("peer_id");
        this.createConversation(clientId, peerId);
        break;

      case "sendMessage":

        String text = call.argument("text");
        byte[] bytes = call.argument("file");
        int messageType = call.argument("messageType");

        this.sendMessage(text,bytes,messageType);
        break;

      case "queryHistoryConversations":

        int limit = call.argument("limit");
        int offset = call.argument("offset");
        this.queryHistoryConversations(limit, offset);
        break;

      case "queryHistoryConversationMessages":

        limit = call.argument("limit");
        String messageId = call.argument("message_id");
        long timestamp = call.argument("timestamp");
        this.queryHistoryConversationMessages(limit,messageId,timestamp);
        break;

      default:
        result.notImplemented();
        break;
    }
  }

  /*
   *  开启聊天
   * */
  private void initSetting(String appId, String appKey, String api,boolean debug) {

    if (debug){
      AVOSCloud.setLogLevel(AVLogger.Level.DEBUG);
    }
    AVOSCloud.initialize(context, appId, appKey, api);
    AVIMOptions.getGlobalOptions().setUnreadNotificationEnabled(true);
    this.setConversationEventHandler();
    initFlutterChannels();
  }

  /*
   *  初始化聊天信息
   * */
  private void login(String userId, final Result result) {

    setPushSetting(userId);
    this.client = AVIMClient.getInstance(userId);
    this.client.open(new AVIMClientCallback() {
      @Override
      public void done(AVIMClient client, AVIMException e) {
        if (e == null) {
          // 成功打开连接
          System.out.println("聊天功能建立成功！");
        }
      }
    });
  }

  /**
   * 初始化推送，订阅clientId所在的频道
   * @param clientId
   */
  private void setPushSetting(String clientId){
    PushService.setDefaultChannelId(context, "default");//这个channel和订阅的channel不一样，只能为default
    PushService.subscribe(context,clientId,activity.getClass()); //订阅频道,这一步必须，否则无法收到推送
    PushService.setDefaultPushCallback(context,activity.getClass());

    AVInstallation.getCurrentInstallation().saveInBackground().subscribe(new Observer<AVObject>() {
      @Override
      public void onSubscribe(Disposable d) {
      }
      @Override
      public void onNext(AVObject avObject) {
        // 关联 installationId 到用户表等操作。
        String installationId = AVInstallation.getCurrentInstallation().getInstallationId();
        System.out.println("保存成功：" + installationId );
      }
      @Override
      public void onError(Throwable e) {
        System.out.println("保存失败，错误信息：" + e.getMessage());
      }
      @Override
      public void onComplete() {
      }
    });
  }
  /*
   * 接收会话handler
   * 接收消息handle
   */
  private void setConversationEventHandler(){
    conversationEventHandler = new LCConversationEventHandler();
    messageHandler = new LCMessageHandler();

    AVIMMessageManager.setConversationEventHandler(conversationEventHandler);
    AVIMMessageManager.registerDefaultMessageHandler(messageHandler);

  }




  /*
   * 初始化flutter和android之间的信道
   * */
  private void initFlutterChannels() {
    setConversationMessageEventToFlutter();
    setConversationEventToFlutter();
    setNotificationEventToFlutter();
  }


  private void createConversation(String clientId, String peerId) {
    this.client.createConversation(Arrays.asList(peerId), clientId + "&" + peerId, null, false, true,
            new AVIMConversationCreatedCallback() {
              @Override
              public void done(AVIMConversation con, AVIMException e) {
                if (e == null) {
                  // 创建成功
                  System.out.println("会话创建成功");
                  conversation = con;
                  conversation.read();
                  queryHistoryConversationMessages(10,null,0);
                }
              }
            });
  }

  private void sendMessage(String text,byte[] file,int messageType){


    if (messageType == AVIMMessageType.TEXT_MESSAGE_TYPE) {
      this.sendMessage(text);
    }else if(messageType == AVIMMessageType.IMAGE_MESSAGE_TYPE){
      this.sendImageMessage(text,file);
    }else if(messageType == AVIMMessageType.AUDIO_MESSAGE_TYPE){
      this.sendAudioMessage(text,file);
    }else if(messageType == AVIMMessageType.VIDEO_MESSAGE_TYPE){
      this.sendVideoMessage(text,file);
    }
  }

  private void sendMessage(String text){

    AVIMTextMessage msg = new AVIMTextMessage();
    msg.setText(text);

    AVIMMessageOption option = new AVIMMessageOption();
    String pushMessage = "{\"alert\":\"您有一条未读的消息\"}";
    option.setPushData(pushMessage);
    conversation.sendMessage(msg,option,new AVIMConversationCallback() {
      @Override
      public void done(AVIMException e) {
        if (e == null) {
          System.out.println("消息发送成功");

        }
      }
    });
  }

  private void sendImageMessage(String text,byte[] image){

    AVFile file = new AVFile("image",image);
    AVIMImageMessage msg = new AVIMImageMessage(file);
    msg.setText(text);

    conversation.sendMessage(msg, new AVIMConversationCallback() {
      @Override
      public void done(AVIMException e) {
        if (e == null) {
          System.out.println("消息发送成功");

        }
      }
    });
  }

  private void sendAudioMessage(String text,byte[] audio){

    AVFile file = new AVFile("audio",audio);
    AVIMAudioMessage msg = new AVIMAudioMessage(file);
    msg.setText(text);

    conversation.sendMessage(msg, new AVIMConversationCallback() {
      @Override
      public void done(AVIMException e) {
        if (e == null) {
          System.out.println("消息发送成功");

        }
      }
    });
  }

  private void sendVideoMessage(String text,byte[] video){

    AVFile file = new AVFile("video",video);
    AVIMVideoMessage msg = new AVIMVideoMessage(file);
    msg.setText(text);

    conversation.sendMessage(msg, new AVIMConversationCallback() {
      @Override
      public void done(AVIMException e) {
        if (e == null) {
          System.out.println("消息发送成功");

        }
      }
    });
  }



  private void queryHistoryConversationMessages(int limit, String messageId, long timestamp) {

    if (messageId == null) {
      this.conversation.queryMessages(limit, new AVIMMessagesQueryCallback() {
        @Override
        public void done(List<AVIMMessage> messages, AVIMException e) {
          if (e == null) {
//            System.out.println("AVIMMessage messages:"+messages);
            convertConversationMessagesToFlutterArray(messages);
          }
        }
      });
    } else {
      this.conversation.queryMessages(messageId, timestamp, limit,
              new AVIMMessagesQueryCallback() {
                @Override
                public void done(List<AVIMMessage> messagesInPage, AVIMException e) {
                  if (e == null) {
                    // 查询成功返回
//                    System.out.println("AVIMMessage messages:"+messagesInPage);
                    convertConversationMessagesToFlutterArray(messagesInPage);

                  }
                }
              });
    }
  }

  private void queryHistoryConversations(int limit, int offset) {
    AVIMConversationsQuery query = this.client.getConversationsQuery();
    query.limit(limit);
    query.skip(offset);
    query.setQueryPolicy(AVQuery.CachePolicy.NETWORK_ELSE_CACHE);
    query.setCacheMaxAge(24 * 60 * 60);
    query.setWithLastMessagesRefreshed(true);
    query.findInBackground(new AVIMConversationQueryCallback() {
      @Override
      public void done(List<AVIMConversation> convs, AVIMException e) {
        if (e == null) {
          // convs 就是想要的结果
          convertConversationsToFlutterArray(convs);
        }
      }
    });
  }

  private void convertConversationsToFlutterArray(List<AVIMConversation> convs) {

    ArrayList conversations = new ArrayList();
    for (AVIMConversation con : convs) {

      if(con.getLastMessage() == null){
        continue;
      }

      SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      String dateString = formatter.format(con.getLastMessageAt());
      Map<String, Object> dic = new HashMap<>();
      dic.put("conversationId", con.getConversationId());
      dic.put("members", con.getMembers());
      dic.put("clientId", this.client.getClientId());
      dic.put("unreadMessagesCount", con.getUnreadMessagesCount());
      dic.put("lastMessage", con.getLastMessage().getContent());
      dic.put("lastMessageAt", dateString);
      conversations.add(dic);
    }

    if (conversationEventCallback != null){
      conversationEventCallback.success(conversations);
    }
  }

  private void convertConversationMessagesToFlutterArray(List<AVIMMessage> mess) {

    ArrayList messages = new ArrayList();
    for (AVIMMessage message : mess) {
      Map<String, Object> dic = new HashMap<>();
      dic.put("messageId", message.getMessageId());
      dic.put("clientId", message.getFrom());
      dic.put("conversationId", message.getConversationId());
      dic.put("content", message.getContent());
      dic.put("timestamp", message.getTimestamp());
      if (this.client.getClientId().equals(message.getFrom())){
        dic.put("ioType", AVIMMessage.AVIMMessageIOType.AVIMMessageIOTypeOut.getIOType());
      }else {
        dic.put("ioType", AVIMMessage.AVIMMessageIOType.AVIMMessageIOTypeIn.getIOType());
      }
      messages.add(dic);
    }

    if (messageEventCallback != null){
      messageEventCallback.success(messages);
    }
  }


  void setConversationEventToFlutter() {

    new EventChannel(messenger, FLUTTER_CHANNEL_CONVERSATION).setStreamHandler(
            new EventChannel.StreamHandler() {
              @Override
              // 这个onListen是Flutter端开始监听这个channel时的回调，第二个参数 EventSink是用来传数据的载体。
              public void onListen(Object arguments, EventChannel.EventSink events) {
                if (events != null) {
                  conversationEventCallback = events;
                  conversationEventHandler.conversationEventCallback = events;
                }
              }

              @Override
              public void onCancel(Object arguments) {
                // 对面不再接收

              }
            }
    );
  }

  void setConversationMessageEventToFlutter() {

    new EventChannel(messenger, FLUTTER_CHANNEL_MESSAGE).setStreamHandler(
            new EventChannel.StreamHandler() {
              @Override
              // 这个onListen是Flutter端开始监听这个channel时的回调，第二个参数 EventSink是用来传数据的载体。
              public void onListen(Object arguments, EventChannel.EventSink events) {
                if (events != null) {
                  messageEventCallback = events;
                  messageHandler.messageEventCallback = events;
                }
              }

              @Override
              public void onCancel(Object arguments) {
                // 对面不再接收

              }
            }
    );
  }

  void setNotificationEventToFlutter() {

    new EventChannel(messenger, FLUTTER_CHANNEL_NOTIFICATION).setStreamHandler(
            new EventChannel.StreamHandler() {
              @Override
              // 这个onListen是Flutter端开始监听这个channel时的回调，第二个参数 EventSink是用来传数据的载体。
              public void onListen(Object arguments, EventChannel.EventSink events) {
                if (events != null) {
                  notificationEventCallback = events;
                }
              }

              @Override
              public void onCancel(Object arguments) {
                // 对面不再接收

              }
            }
    );
  }

}
