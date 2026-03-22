import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

const TimelineScreen: React.FC = () => {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Timeline Dashboard</Text>
      <Text>Abnormal situation timeline here.</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  title: { fontSize: 24 },
});

export default TimelineScreen;