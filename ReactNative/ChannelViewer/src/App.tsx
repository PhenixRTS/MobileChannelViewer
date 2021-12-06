/**
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All Rights Reserved.
 */

import React, {useState, useEffect} from 'react';
import {
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
  LogBox
} from 'react-native';
import {
  RTCPeerConnection,
  RTCIceCandidate,
  RTCSessionDescription,
  RTCView
} from 'react-native-webrtc';

import {CustomInput, CustomButton} from './components/ui';

// eslint-disable-next-line no-global-assign
global = Object.assign(global, {
  RTCPeerConnection,
  RTCIceCandidate,
  RTCSessionDescription
});

LogBox.ignoreAllLogs();

const sdk = require('phenix-web-sdk/dist/phenix-web-sdk-react-native');

sdk.RTC.shim(); // Required

const TOKEN = '';

const App = () => {
  const webrtcSupported = sdk.RTC.webrtcSupported;
  const [videoURL, setVideoURL] = useState('');
  const [token, setToken] = useState(TOKEN);
  const [channel, setChannel] = useState();

  useEffect(() => {
    if (token && channel) {

      if (channel) {
        channel.joinChannel(
          {streamToken: token},
          () => {},
          (error: any, response: {status: string, mediaStream: any}) => {
            if (response?.mediaStream) {
              const streamURL = response.mediaStream.getStream().toURL();
              setVideoURL(streamURL);
            }
          }
        );
      }
    }
  }, [channel]);

  const onSubmit = () => {
    const channelExpress = new sdk.express.ChannelExpress({authToken: token});
    setChannel(channelExpress);
  };

  const onCancel = () => {
    if (channel) {
      channel.dispose();
    }
  };

  return (
    <View style={styles.wrapper}>
      <SafeAreaView style={styles.flex}>
        <ScrollView style={styles.flex}>
          <View style={styles.container}>
            <Text>{'WebRTC is supported: ' + (webrtcSupported ? 'YES' : 'NO')}</Text>

            <RTCView
              style={styles.video}
              streamURL={videoURL}
            />

            <View style={styles.inputWrapper}>
              <CustomInput
                value={token}
                placeholder={'Enter Token'}
                onChangeText={setToken}
                onClear={() => setToken('')}
              />
            </View>

            <View style={styles.btns}>
              <View style={styles.btn}>
                <CustomButton
                  title={'SUBMIT'}
                  onPress={onSubmit}
                />
              </View>
              <View style={styles.btn}>
                <CustomButton
                  title={'CANCEL'}
                  onPress={onCancel}
                  color="#333"
                />
              </View>
            </View>

          </View>
        </ScrollView>
      </SafeAreaView>
    </View>
  );
};

export default App;

const styles = StyleSheet.create({
  flex: {flex: 1},
  wrapper: {
    flex: 1,
    backgroundColor: '#fff'
  },
  container: {
    flex: 1,
    padding: 16,
    alignItems: 'center'
  },
  video: {
    height: 360,
    width: '100%'
  },
  inputWrapper: {
    flex: 1,
    width: '100%',
    marginTop: 8
  },
  btns: {
    flex: 1,
    flexDirection: 'row',
    marginTop: 16,
    justifyContent: 'space-between'
  },
  btn: {
    minWidth: 100,
    margin: 16
  }
});