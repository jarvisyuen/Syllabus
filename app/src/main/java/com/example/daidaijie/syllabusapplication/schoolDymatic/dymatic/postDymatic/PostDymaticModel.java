package com.example.daidaijie.syllabusapplication.schoolDymatic.dymatic.postDymatic;

import android.support.annotation.Nullable;
import android.util.Log;

import com.example.daidaijie.syllabusapplication.App;
import com.example.daidaijie.syllabusapplication.bean.BmobPhoto;
import com.example.daidaijie.syllabusapplication.bean.HttpResult;
import com.example.daidaijie.syllabusapplication.bean.PhotoInfo;
import com.example.daidaijie.syllabusapplication.bean.PostActivityBean;
import com.example.daidaijie.syllabusapplication.bean.QiNiuImageInfo;
import com.example.daidaijie.syllabusapplication.bean.SmmsResult;
import com.example.daidaijie.syllabusapplication.bean.UserInfo;
import com.example.daidaijie.syllabusapplication.retrofitApi.PostActivityApi;
import com.example.daidaijie.syllabusapplication.retrofitApi.PushImageToSmmsApi;
import com.example.daidaijie.syllabusapplication.user.IUserModel;
import com.example.daidaijie.syllabusapplication.util.GsonUtil;
import com.example.daidaijie.syllabusapplication.util.LoggerUtil;
import com.example.daidaijie.syllabusapplication.util.RetrofitUtil;
import com.orhanobut.logger.Logger;

import org.joda.time.LocalDateTime;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by daidaijie on 2016/10/31.
 */

public class PostDymaticModel implements IPostDymaticModel {
    private String TAG = this.getClass().getSimpleName();

    private PostActivityApi mPostActivityApi;

    private PushImageToSmmsApi pushImageToSmmsApi;

    private List<String> mPhotoImgs;

    private IUserModel mIUserModel;

    LocalDateTime mStartTime;
    LocalDateTime mEndTime;

    public PostDymaticModel(PostActivityApi postActivityApi, IUserModel IUserModel, PushImageToSmmsApi pushImageToSmmsApi) {
        mPostActivityApi = postActivityApi;
        mIUserModel = IUserModel;
        mPhotoImgs = new ArrayList<>();
        mStartTime = LocalDateTime.now();
        mEndTime = LocalDateTime.now();
        this.pushImageToSmmsApi = pushImageToSmmsApi;
    }

    @Override
    public LocalDateTime getStartTime() {
        return mStartTime;
    }

    @Override
    public void setStartTime(LocalDateTime startTime) {
        mStartTime = startTime;
    }

    @Override
    public String getStartTimeString() {
        return getTimeString(mStartTime);
    }

    @Override
    public LocalDateTime getEndTime() {
        return mEndTime;
    }

    @Override
    public String getEndTimeString() {
        return getTimeString(mEndTime);
    }

    @Override
    public void setEndTime(LocalDateTime endTime) {
        mEndTime = endTime;
    }

    private String getTimeString(LocalDateTime localDateTime) {
        return String.format("%4d.%02d.%02d %02d:%02d", localDateTime.getYear(),
                localDateTime.getMonthOfYear(), localDateTime.getDayOfMonth(),
                localDateTime.getHourOfDay(), localDateTime.getMinuteOfHour());
    }

    @Override
    public List<String> getPhotoImgs() {
        return mPhotoImgs;
    }

    @Override
    public void postPhotoToSmms(final OnPostCallBack onPostCallBack) {
        if (mPhotoImgs.size() == 0) {
            onPostCallBack.onSuccess(null);
            return;
        }

        Observable.from(mPhotoImgs)
                .subscribeOn(Schedulers.io())
                .map(new Func1<String, File>() {
                    @Override
                    public File call(String s) {
                        return new File(s.substring("file://".length(), s.length()));
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<File>() {
                    PhotoInfo photoInfo = new PhotoInfo();

                    @Override
                    public void onStart() {
                        super.onStart();
                        photoInfo.setPhoto_list(new ArrayList<PhotoInfo.PhotoListBean>());
                    }

                    @Override
                    public void onCompleted() {
                        String photoListJsonString = GsonUtil.getDefault()
                                .toJson(photoInfo);
                        onPostCallBack.onSuccess(photoListJsonString);
                    }

                    @Override
                    public void onError(Throwable e) {
                        LoggerUtil.printStack(e);
                        onPostCallBack.onFail("图片上传失败");
                    }

                    @Override
                    public void onNext(File file) {
                        //将图片上传到sm.ms
                        Log.d(TAG, "postPhotoToSmms: " + file.getName());
                        RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
                        MultipartBody.Part body = MultipartBody.Part.createFormData("smfile", file.getName(), requestFile);
                        pushImageToSmmsApi.pushImage(body)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Subscriber<SmmsResult>() {
                                    @Override
                                    public void onCompleted() {
                                        Log.d(TAG, "onCompleted: ");
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        onPostCallBack.onFail("图片上传失败!");
                                    }

                                    @Override
                                    public void onNext(SmmsResult smmsResult) {
                                        Log.d(TAG, "onNext: " + smmsResult.getStatus());
                                        if (smmsResult.getStatus().equals("success")) {
                                            Log.d(TAG, "onNext: " + smmsResult.getData().getPicUrl());
                                            PhotoInfo.PhotoListBean photoListBean = new PhotoInfo.PhotoListBean();
                                            photoListBean.setSize_big(smmsResult.getData().getPicUrl());
                                            photoListBean.setSize_small(smmsResult.getData().getPicUrl());
                                            photoInfo.getPhoto_list().add(photoListBean);
                                            if (photoInfo.getPhoto_list().size() == mPhotoImgs.size()) {
                                                //将json post到服务器
                                                String photoListJsonString = GsonUtil.getDefault()
                                                        .toJson(photoInfo);
                                                LoggerUtil.e(photoListJsonString);
                                                onPostCallBack.onSuccess(photoListJsonString);
                                            }
                                        }
                                    }
                                });
                    }
                });
    }

    @Override
    public Observable<Void> pushContent(@Nullable String photoListJson,
                                        String msg, String source, String url, String locate, boolean hasTime) {

        PostActivityBean postActivityBean = new PostActivityBean();
        UserInfo mUserInfo = mIUserModel.getUserInfoNormal();
        postActivityBean.photo_list_json = photoListJson;
        postActivityBean.uid = mUserInfo.getUser_id();
        postActivityBean.token = mUserInfo.getToken();
        postActivityBean.content = url;
        postActivityBean.source = source;
        postActivityBean.description = msg;
        postActivityBean.activity_location = locate;

        Long startTime = null;
        Long endTime = null;
        if (hasTime) {
            startTime = mStartTime.toDate().getTime();
            endTime = mEndTime.toDate().getTime();

            if (endTime < startTime) {
                return Observable.error(new Throwable("结束时间不能少于开始时间"));
            }

            postActivityBean.activity_start_time = startTime / 1000;
            postActivityBean.activity_end_time = endTime / 1000;
        }

        return mPostActivityApi.post(postActivityBean)
                .subscribeOn(Schedulers.io())
                .flatMap(new Func1<HttpResult<Void>, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(HttpResult<Void> voidHttpResult) {
                        Logger.t("PostActivity").json(GsonUtil.getDefault().toJson(voidHttpResult));
                        if (RetrofitUtil.isSuccessful(voidHttpResult)
                                || voidHttpResult.getCode() == 201) {
                            return Observable.just(voidHttpResult.getData());
                        }
                        return Observable.error(new Throwable(voidHttpResult.getMessage()));
                    }
                }).observeOn(AndroidSchedulers.mainThread());

    }
}
